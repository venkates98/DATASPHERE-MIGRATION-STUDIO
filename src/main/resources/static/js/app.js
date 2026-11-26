/*
 * DataSphere Migration Studio — shared UI behaviour.
 * - Collapsible/off-canvas sidebar (only the collapsed flag is kept in localStorage; never credentials).
 * - Toast notifications (DataSphere.notify) and server flash messages.
 * - Confirmation dialogs for forms/buttons carrying data-ds-confirm (no browser alert/confirm boxes).
 * - Loading state for forms carrying data-ds-loading.
 * - CSRF-aware fetch helper (DataSphere.fetchJson).
 */
(function () {
    'use strict';

    var COLLAPSE_KEY = 'ds.sidebar.collapsed';
    var body = document.body;
    var desktop = window.matchMedia('(min-width: 992px)');

    /* ---------- Sidebar ---------- */
    var toggleBtn = document.getElementById('ds-sidebar-toggle');

    function setCollapsed(collapsed) {
        body.classList.toggle('ds-sidebar-collapsed', collapsed);
        if (toggleBtn) {
            toggleBtn.setAttribute('aria-expanded', collapsed ? 'false' : 'true');
        }
    }

    try {
        setCollapsed(localStorage.getItem(COLLAPSE_KEY) === '1');
    } catch (e) {
        setCollapsed(false); // storage may be unavailable (privacy mode); the UI still works
    }

    if (toggleBtn) {
        toggleBtn.addEventListener('click', function () {
            if (desktop.matches) {
                var collapsed = !body.classList.contains('ds-sidebar-collapsed');
                setCollapsed(collapsed);
                try { localStorage.setItem(COLLAPSE_KEY, collapsed ? '1' : '0'); } catch (e) { /* preference only */ }
            } else {
                var open = !body.classList.contains('ds-sidebar-open');
                body.classList.toggle('ds-sidebar-open', open);
                toggleBtn.setAttribute('aria-expanded', open ? 'true' : 'false');
            }
        });
    }
    document.querySelectorAll('[data-ds-sidebar-close]').forEach(function (el) {
        el.addEventListener('click', function () { body.classList.remove('ds-sidebar-open'); });
    });
    document.addEventListener('keydown', function (e) {
        if (e.key === 'Escape') { body.classList.remove('ds-sidebar-open'); }
    });

    /* ---------- Tooltips ---------- */
    if (window.bootstrap) {
        document.querySelectorAll('[data-bs-toggle="tooltip"]').forEach(function (el) {
            new bootstrap.Tooltip(el);
        });
    }

    /* ---------- Notifications ---------- */
    var ICONS = {
        success: 'bi-check-circle-fill text-success',
        info: 'bi-info-circle-fill text-primary',
        warning: 'bi-exclamation-triangle-fill text-warning',
        error: 'bi-x-octagon-fill text-danger'
    };
    var TITLES = { success: 'Success', info: 'Information', warning: 'Warning', error: 'Error' };

    function notify(type, message, options) {
        var container = document.getElementById('ds-toast-container');
        if (!container || !window.bootstrap) { return; }
        type = ICONS[type] ? type : 'info';
        var toast = document.createElement('div');
        toast.className = 'toast ds-toast ds-toast-' + type;
        toast.setAttribute('role', type === 'error' ? 'alert' : 'status');
        toast.setAttribute('aria-live', type === 'error' ? 'assertive' : 'polite');
        toast.setAttribute('aria-atomic', 'true');

        var header = document.createElement('div');
        header.className = 'toast-header';
        var icon = document.createElement('i');
        icon.className = 'bi me-2 ' + ICONS[type];
        icon.setAttribute('aria-hidden', 'true');
        var title = document.createElement('strong');
        title.className = 'me-auto';
        title.textContent = (options && options.title) || TITLES[type];
        var close = document.createElement('button');
        close.type = 'button';
        close.className = 'btn-close';
        close.setAttribute('data-bs-dismiss', 'toast');
        close.setAttribute('aria-label', 'Close');
        header.append(icon, title, close);

        var bodyEl = document.createElement('div');
        bodyEl.className = 'toast-body';
        bodyEl.textContent = message; // textContent: never interpret messages as HTML

        toast.append(header, bodyEl);
        container.appendChild(toast);
        toast.addEventListener('hidden.bs.toast', function () { toast.remove(); });
        new bootstrap.Toast(toast, { delay: type === 'error' ? 10000 : 5000 }).show();
    }

    document.querySelectorAll('[data-ds-flash]').forEach(function (el) {
        notify(el.getAttribute('data-ds-flash'), el.getAttribute('data-message') || '');
    });

    /* ---------- Confirmation dialog ---------- */
    var modalEl = document.getElementById('ds-confirm-modal');
    var modal = modalEl && window.bootstrap ? new bootstrap.Modal(modalEl) : null;
    var pendingAction = null;

    function confirmDialog(opts) {
        if (!modal) { return Promise.resolve(false); }
        document.getElementById('ds-confirm-title').textContent = opts.title || 'Please confirm';
        document.getElementById('ds-confirm-body').textContent = opts.message || 'Are you sure?';
        var ok = document.getElementById('ds-confirm-ok');
        ok.textContent = opts.confirmLabel || 'Confirm';
        ok.className = 'btn ' + (opts.style === 'danger' ? 'btn-danger' : 'btn-primary');
        return new Promise(function (resolve) {
            pendingAction = resolve;
            modal.show();
        });
    }

    if (modalEl) {
        document.getElementById('ds-confirm-ok').addEventListener('click', function () {
            var resolve = pendingAction;
            pendingAction = null;
            modal.hide();
            if (resolve) { resolve(true); }
        });
        modalEl.addEventListener('hidden.bs.modal', function () {
            if (pendingAction) { pendingAction(false); pendingAction = null; }
        });
    }

    document.addEventListener('submit', function (e) {
        var form = e.target;
        if (!(form instanceof HTMLFormElement)) { return; }

        if (form.hasAttribute('data-ds-confirm') && !form.dataset.dsConfirmed) {
            e.preventDefault();
            confirmDialog({
                title: form.getAttribute('data-ds-confirm-title'),
                message: form.getAttribute('data-ds-confirm'),
                confirmLabel: form.getAttribute('data-ds-confirm-label'),
                style: form.getAttribute('data-ds-confirm-style')
            }).then(function (accepted) {
                if (accepted) {
                    form.dataset.dsConfirmed = 'true';
                    form.requestSubmit ? form.requestSubmit() : form.submit();
                }
            });
            return;
        }

        if (form.hasAttribute('data-ds-loading')) {
            var btn = form.querySelector('[type="submit"]');
            if (btn) {
                btn.disabled = true;
                btn.setAttribute('aria-busy', 'true');
            }
        }
    });

    /* ---------- Progress bars (width from data attribute; avoids inline styles under CSP) ---------- */
    document.querySelectorAll('[data-ds-width]').forEach(function (el) {
        el.style.width = el.getAttribute('data-ds-width') + '%';
    });

    /* ---------- CSRF-aware fetch ---------- */
    function csrf() {
        var token = document.querySelector('meta[name="_csrf"]');
        var header = document.querySelector('meta[name="_csrf_header"]');
        return token && header ? { header: header.content, token: token.content } : null;
    }

    function fetchJson(url, options) {
        options = options || {};
        var headers = Object.assign({ 'Accept': 'application/json' }, options.headers || {});
        var method = (options.method || 'GET').toUpperCase();
        if (method !== 'GET' && method !== 'HEAD') {
            var c = csrf();
            if (c) { headers[c.header] = c.token; }
            if (options.json !== undefined) {
                headers['Content-Type'] = 'application/json';
                options.body = JSON.stringify(options.json);
            }
        }
        return fetch(url, Object.assign({}, options, { method: method, headers: headers, credentials: 'same-origin' }))
            .then(function (response) {
                if (response.redirected && response.url.indexOf('/login') !== -1) {
                    window.location.href = response.url; // session expired
                    return Promise.reject(new Error('Session expired'));
                }
                return response.json().catch(function () { return {}; }).then(function (data) {
                    if (!response.ok) {
                        var err = new Error(data.message || 'Request failed');
                        err.details = data; // contains code, suggestedAction, correlationId
                        throw err;
                    }
                    return data;
                });
            });
    }

    window.DataSphere = { notify: notify, confirm: confirmDialog, fetchJson: fetchJson };
})();
