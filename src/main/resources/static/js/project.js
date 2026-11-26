/*
 * Migration project workspace: connections, tables, mapping review, validation/approval and preview.
 *
 * The browser only displays and requests. Every rule (what may be approved, whether a migration may proceed)
 * is enforced again by the server, so hiding or enabling a button here is a convenience, not a safeguard.
 * All text from the server is inserted with textContent, never as HTML.
 */
(function () {
    'use strict';

    var root = document.getElementById('project-root');
    if (!root) { return; }
    var projectId = root.getAttribute('data-project-id');
    var PROJECT_URL = '/api/projects/' + encodeURIComponent(projectId);
    var MAPPING_URL = '/api/mapping/' + encodeURIComponent(projectId);
    var STEPS = ['connections', 'tables', 'mapping', 'approve', 'preview'];

    var project = null;
    var currentStep = null;
    var pendingPairs = [];            // table pairs being chosen in step 2: {source, target, note}
    var tableLists = { source: [], target: [] };
    var schemasLoaded = false;
    var filters = {};                 // per table pair: {text, status}
    var transformCache = {};          // "SRC|TGT" -> Promise of applicable conversions
    var lastDefaults = { source: null, target: null };

    var STATUS_LABEL = {
        SUGGESTED: 'Suggested', NEEDS_REVIEW: 'Needs review', AMBIGUOUS: 'Ambiguous', ACCEPTED: 'Accepted',
        MODIFIED: 'Modified', REJECTED: 'Rejected', UNMAPPED: 'Unmapped'
    };
    var STATUS_CHIP = {
        SUGGESTED: 'ds-chip-info', NEEDS_REVIEW: 'ds-chip-warning', AMBIGUOUS: 'ds-chip-warning',
        ACCEPTED: 'ds-chip-success', MODIFIED: 'ds-chip-info', REJECTED: 'ds-chip-danger', UNMAPPED: 'ds-chip-muted'
    };
    var CONFIDENCE_CLASS = { HIGH: 'high', GOOD: 'good', REVIEW: 'review', WEAK: 'weak' };
    var CONFIDENCE_LABEL = { HIGH: 'High', GOOD: 'Good', REVIEW: 'Review', WEAK: 'Weak' };

    /* ------------------------------------------------------------------ helpers */

    function $(id) { return document.getElementById(id); }

    function el(tag, className, text) {
        var node = document.createElement(tag);
        if (className) { node.className = className; }
        if (text !== undefined && text !== null) { node.textContent = text; }
        return node;
    }

    function icon(name) {
        var i = el('i', 'bi ' + name);
        i.setAttribute('aria-hidden', 'true');
        return i;
    }

    function describe(err) {
        var d = (err && err.details) || {};
        var lines = [err && err.message ? err.message : 'Request failed'];
        (d.fieldIssues || []).forEach(function (i) { lines.push(i.message); });
        (d.details || []).forEach(function (x) { lines.push(x); });
        if (d.suggestedAction) { lines.push(d.suggestedAction); }
        if (d.code) { lines.push('Error ' + d.code + (d.correlationId ? ' \u00b7 reference ' + d.correlationId : '')); }
        return lines;
    }

    function showError(box, err) {
        box.textContent = (Array.isArray(err) ? err : describe(err)).join('\n');
        box.hidden = false;
    }

    function toastError(err) {
        DataSphere.notify('error', describe(err).slice(0, 3).join(' '));
    }

    function busy(button, promise) {
        var wasDisabled = button.disabled;
        button.disabled = true;
        button.setAttribute('aria-busy', 'true');
        return promise.then(function (r) {
            button.removeAttribute('aria-busy');
            button.disabled = wasDisabled;
            return r;
        }, function (e) {
            button.removeAttribute('aria-busy');
            button.disabled = wasDisabled;
            throw e;
        });
    }

    function formatTime(iso) {
        if (!iso) { return ''; }
        var d = new Date(iso);
        return isNaN(d.getTime()) ? '' : d.toLocaleString();
    }

    function keyOf(schema, name) { return schema ? schema + '.' + name : name; }

    function clear(node) { node.textContent = ''; }

    function post(url, json) { return DataSphere.fetchJson(url, { method: 'POST', json: json === undefined ? {} : json }); }

    /* ------------------------------------------------------------------ loading and top-level render */

    function load(thenStep) {
        return DataSphere.fetchJson(PROJECT_URL).then(function (p) {
            setProject(p);
            if (thenStep) { showStep(thenStep); }
            return p;
        });
    }

    function setProject(p) {
        project = p;
        renderBar();
        renderConnections();
        renderPairsList();
        renderMapping();
        renderApprove();
        renderPreview();
        renderStepper();
    }

    function stateChipClass(state) {
        if (state === 'MAPPING_APPROVED' || state === 'READY_FOR_MIGRATION') { return 'ds-chip-success'; }
        if (state === 'MAPPING_VALIDATION_FAILED') { return 'ds-chip-danger'; }
        if (state === 'MAPPING_MODIFIED' || state === 'MAPPING_REVIEW_REQUIRED') { return 'ds-chip-warning'; }
        if (state === 'SUGGESTIONS_GENERATED' || state === 'DISCOVERED') { return 'ds-chip-info'; }
        return 'ds-chip-muted';
    }

    function renderBar() {
        var chip = $('project-state-chip');
        chip.className = 'ds-chip ' + stateChipClass(project.state);
        chip.textContent = project.stateLabel;
        $('project-state-text').textContent = project.stateDescription;
        $('project-version').textContent = String(project.mappingVersion);
    }

    function progress() {
        var counts = mappingCounts();
        return {
            connections: !!(project.source && project.target),
            tables: project.tablePairs.length > 0,
            mapping: project.capabilities.canEditMapping && counts.awaiting === 0,
            approve: !!project.approval,
            preview: !!project.previewVerifiedAt
        };
    }

    function renderStepper() {
        var done = progress();
        document.querySelectorAll('.ds-step').forEach(function (btn) {
            var step = btn.getAttribute('data-step');
            btn.classList.toggle('is-active', step === currentStep);
            btn.classList.toggle('is-done', !!done[step] && step !== currentStep);
            if (step === currentStep) { btn.setAttribute('aria-current', 'step'); } else { btn.removeAttribute('aria-current'); }
        });
    }

    function showStep(step) {
        currentStep = step;
        STEPS.forEach(function (s) { $('step-' + s).hidden = s !== step; });
        renderStepper();
        if (step === 'tables') { prepareTablesStep(); }
        window.scrollTo(0, 0);
    }

    document.querySelectorAll('.ds-step').forEach(function (btn) {
        btn.addEventListener('click', function () { showStep(btn.getAttribute('data-step')); });
    });

    function firstIncompleteStep() {
        var done = progress();
        for (var i = 0; i < STEPS.length; i++) {
            if (!done[STEPS[i]]) { return STEPS[i]; }
        }
        return 'preview';
    }

    /* ------------------------------------------------------------------ 1. connections */

    var ROLES = ['source', 'target'];

    function roleForm(role) { return $(role + '-form'); }

    function typeOption(role) { var s = $(role + '-type'); return s.options[s.selectedIndex]; }

    function applyTypeDefaults(role, forcePort) {
        var option = typeOption(role);
        var portInput = $(role + '-port');
        var previous = lastDefaults[role];
        var defaultPort = option.getAttribute('data-port');
        if (forcePort || !portInput.value || portInput.value === previous) {
            portInput.value = defaultPort;
        }
        lastDefaults[role] = defaultPort;
        $(role + '-database-label').textContent = option.getAttribute('data-db-label') || 'Database';
        $(role + '-trust').disabled = $(role + '-type').value !== 'SQLSERVER';
        var schemaWrap = $(role + '-schema-wrap');
        if (schemaWrap) {
            schemaWrap.hidden = option.getAttribute('data-db-is-schema') === 'true';
        }
        updatePairBanner();
        updateUrlPreview(role);
    }

    function updatePairBanner() {
        var banner = $('pair-banner');
        if (!banner) { return; }
        var src = $('source-type');
        var tgt = $('target-type');
        if (!src || !tgt) { return; }
        banner.textContent = (src.options[src.selectedIndex].text || src.value) + '  \u2192  ' +
            (tgt.options[tgt.selectedIndex].text || tgt.value) +
            '. Source and target may be the same product.';
    }

    function updateUrlPreview(role) {
        var elp = $(role + '-url-preview');
        if (!elp) { return; }
        var type = $(role + '-type').value;
        var host = $(role + '-host').value.trim() || 'host';
        var port = $(role + '-port').value || '';
        var db = $(role + '-database').value.trim() || 'database';
        var url = '';
        if (type === 'SQLSERVER') {
            url = 'jdbc:sqlserver://' + host + ':' + port + ';databaseName=' + db + ';encrypt=true';
        } else if (type === 'ORACLE') {
            url = 'jdbc:oracle:thin:@//' + host + ':' + port + '/' + db;
        } else if (type === 'POSTGRESQL') {
            url = 'jdbc:postgresql://' + host + ':' + port + '/' + db;
        } else if (type === 'MYSQL') {
            url = 'jdbc:mysql://' + host + ':' + port + '/' + db;
        }
        elp.textContent = url ? ('Connection URL (password is never included): ' + url) : '';
    }

    function fillConnection(role, c) {
        if (!c) { return; }
        $(role + '-type').value = c.type;
        applyTypeDefaults(role, false);
        $(role + '-host').value = c.host;
        $(role + '-port').value = c.port;
        $(role + '-database').value = c.database;
        $(role + '-username').value = c.username;
        if ($(role + '-schema') && c.schema) { $(role + '-schema').value = c.schema; }
        $(role + '-trust').checked = !!c.trustServerCertificate;
    }

    function renderConnections() {
        ROLES.forEach(function (role) {
            var c = project[role];
            var chip = $(role + '-status');
            if (!c) {
                chip.className = 'ds-chip ds-chip-muted';
                chip.textContent = 'Not saved';
                return;
            }
            if (!roleForm(role).dataset.filled) {
                fillConnection(role, c);
                roleForm(role).dataset.filled = 'true';
            }
            if (c.credentialsAvailable) {
                chip.className = 'ds-chip ds-chip-success';
                chip.textContent = 'Saved \u00b7 ready';
            } else {
                chip.className = 'ds-chip ds-chip-warning';
                chip.textContent = 'Saved \u00b7 enter password';
            }
        });
    }

    function readConnection(role) {
        var form = roleForm(role);
        var data = {
            type: $(role + '-type').value,
            host: $(role + '-host').value.trim(),
            port: parseInt($(role + '-port').value, 10),
            database: $(role + '-database').value.trim(),
            schema: $(role + '-schema') && !$(role + '-schema-wrap').hidden ? $(role + '-schema').value.trim() : null,
            username: $(role + '-username').value.trim(),
            password: $(role + '-password').value,
            trustServerCertificate: $(role + '-trust').checked && !$(role + '-trust').disabled
        };
        var problems = [];
        if (!data.host) { problems.push('Enter the host.'); }
        if (!(data.port >= 1 && data.port <= 65535)) { problems.push('Enter a port between 1 and 65535.'); }
        if (!data.database) { problems.push('Enter the ' + ($(role + '-database-label').textContent || 'database').toLowerCase() + '.'); }
        if (!data.username) { problems.push('Enter the user name.'); }
        if (!data.password) { problems.push('Enter the password.'); }
        return { data: data, problems: problems, form: form };
    }

    ROLES.forEach(function (role) {
        $(role + '-type').addEventListener('change', function () { applyTypeDefaults(role, false); });
        ['host', 'port', 'database'].forEach(function (f) {
            $(role + '-' + f).addEventListener('input', function () { updateUrlPreview(role); });
        });
        applyTypeDefaults(role, true);

        function reset() { $(role + '-error').hidden = true; $(role + '-ok').hidden = true; }

        $(role + '-test').addEventListener('click', function () {
            reset();
            var r = readConnection(role);
            if (r.problems.length) { showError($(role + '-error'), r.problems); return; }
            busy($(role + '-test'), post('/api/connections/' + role + '/test', r.data)).then(function (res) {
                var box = $(role + '-ok');
                box.textContent = 'Connected to ' + (res.productName || 'the database') +
                    (res.productVersion ? ' ' + res.productVersion : '') + ' as ' + (res.currentUser || r.data.username) +
                    ' (' + res.latencyMillis + ' ms). Nothing has been saved yet.';
                box.hidden = false;
            }).catch(function (e) { showError($(role + '-error'), e); });
        });

        roleForm(role).addEventListener('submit', function (ev) {
            ev.preventDefault();
            reset();
            var r = readConnection(role);
            if (r.problems.length) { showError($(role + '-error'), r.problems); return; }
            var go = function () {
                busy($(role + '-save'), DataSphere.fetchJson(PROJECT_URL + '/connections/' + role,
                    { method: 'PUT', json: r.data })).then(function (p) {
                    $(role + '-password').value = '';
                    roleForm(role).dataset.filled = '';
                    schemasLoaded = false;
                    setProject(p);
                    var box = $(role + '-ok');
                    box.textContent = 'Connection tested and saved. The password is kept in memory only.';
                    box.hidden = false;
                    if (p.source && p.target) {
                        DataSphere.notify('success', 'Both connections are saved. Continue with step 2, Tables.');
                    }
                }).catch(function (e) { showError($(role + '-error'), e); });
            };
            var existing = project[role];
            if (existing && project.tablePairs.length > 0 && identityChanged(existing, r.data)) {
                DataSphere.confirm({
                    title: 'Change the ' + role + ' database?',
                    message: 'This points the project at a different database. The table selection, mapping and ' +
                        'approval built on the old one will be cleared.',
                    confirmLabel: 'Change database'
                }).then(function (ok) { if (ok) { go(); } });
            } else {
                go();
            }
        });
    });

    function identityChanged(old, now) {
        return old.type !== now.type || old.host.toLowerCase() !== now.host.toLowerCase() || old.port !== now.port ||
            old.database.toLowerCase() !== now.database.toLowerCase() ||
            old.username.toLowerCase() !== now.username.toLowerCase();
    }

    /* ------------------------------------------------------------------ 2. tables */

    function connectionsReady() {
        return project.source && project.target && project.source.credentialsAvailable &&
            project.target.credentialsAvailable;
    }

    function prepareTablesStep() {
        var ready = connectionsReady();
        $('tables-blocked').hidden = ready;
        $('tables-body').hidden = !ready;
        if (!ready) { return; }
        if (!pendingPairs.length && project.tablePairs.length) { loadPendingFromProject(); }
        if (!schemasLoaded) { loadSchemas(); }
    }

    function loadPendingFromProject() {
        pendingPairs = project.tablePairs.map(function (p) {
            return {
                source: { schema: p.sourceSchema, name: p.sourceTable },
                target: { schema: p.targetSchema, name: p.targetTable }, note: 'Saved selection'
            };
        });
        renderPairsList();
    }

    function chooseSchema(schemas, conn) {
        var wanted = [conn.schema, conn.username, 'dbo', 'public', conn.database];
        for (var i = 0; i < wanted.length; i++) {
            if (!wanted[i]) { continue; }
            for (var j = 0; j < schemas.length; j++) {
                if (schemas[j].toLowerCase() === String(wanted[i]).toLowerCase()) { return schemas[j]; }
            }
        }
        return schemas[0];
    }

    function fillSchemaSelect(role, schemas, preferred) {
        var select = $(role + '-schema');
        clear(select);
        if (!schemas.length) { schemas = ['']; }
        schemas.forEach(function (s) {
            var o = el('option', null, s || '(default)');
            o.value = s;
            select.appendChild(o);
        });
        select.value = preferred !== undefined && schemas.indexOf(preferred) >= 0 ? preferred : chooseSchema(schemas, project[role]);
    }

    function loadSchemas() {
        var savedFirst = project.tablePairs.length ? project.tablePairs[0] : null;
        return Promise.all(ROLES.map(function (role) {
            return DataSphere.fetchJson(PROJECT_URL + '/schema/' + role + '/schemas');
        })).then(function (lists) {
            fillSchemaSelect('source', lists[0], savedFirst ? savedFirst.sourceSchema : undefined);
            fillSchemaSelect('target', lists[1], savedFirst ? savedFirst.targetSchema : undefined);
            schemasLoaded = true;
            return loadTables();
        }).catch(function (e) { showError($('tables-error'), e); });
    }

    function fillTableSelect(role, tables) {
        var select = $('pair-' + role);
        clear(select);
        tables.forEach(function (t) {
            var o = el('option', null, t.name + (t.type && t.type !== 'TABLE' ? ' (' + t.type.toLowerCase() + ')' : ''));
            o.value = t.name;
            select.appendChild(o);
        });
        select.disabled = tables.length === 0;
    }

    function loadTables() {
        $('tables-error').hidden = true;
        var sourceSchema = $('source-schema').value;
        var targetSchema = $('target-schema').value;
        var q = function (role, schema) {
            return DataSphere.fetchJson(PROJECT_URL + '/schema/' + role + '/tables?schema=' + encodeURIComponent(schema));
        };
        return busy($('tables-load'), Promise.all([q('source', sourceSchema), q('target', targetSchema)]))
            .then(function (lists) {
                tableLists.source = lists[0];
                tableLists.target = lists[1];
                fillTableSelect('source', lists[0]);
                fillTableSelect('target', lists[1].filter(function (t) { return !t.type || t.type === 'TABLE'; }));
                $('pair-add').disabled = !lists[0].length || !lists[1].length;
                $('tables-suggest').disabled = !lists[0].length || !lists[1].length;
                if (!lists[0].length) {
                    showError($('tables-error'), ['No tables were found in the source schema ' + (sourceSchema || '(default)') +
                        '. Choose another schema, or check the account\'s permissions.']);
                }
            }).catch(function (e) { showError($('tables-error'), e); });
    }

    $('tables-load').addEventListener('click', loadTables);
    $('source-schema').addEventListener('change', function () { loadTables(); });
    $('target-schema').addEventListener('change', function () { loadTables(); });

    function addPair(sourceName, targetName, note) {
        var s = { schema: $('source-schema').value, name: sourceName };
        var t = { schema: $('target-schema').value, name: targetName };
        var duplicate = pendingPairs.some(function (p) {
            return keyOf(p.source.schema, p.source.name) === keyOf(s.schema, s.name) ||
                keyOf(p.target.schema, p.target.name) === keyOf(t.schema, t.name);
        });
        if (duplicate) { return false; }
        pendingPairs.push({ source: s, target: t, note: note || '' });
        return true;
    }

    $('pair-add').addEventListener('click', function () {
        $('tables-error').hidden = true;
        var s = $('pair-source').value;
        var t = $('pair-target').value;
        if (!s || !t) { return; }
        if (!addPair(s, t, 'Chosen by you')) {
            showError($('tables-error'), ['One of these tables is already in the list. A table can be used only once.']);
            return;
        }
        renderPairsList();
    });

    $('tables-suggest').addEventListener('click', function () {
        $('tables-error').hidden = true;
        var url = PROJECT_URL + '/tables/suggest?sourceSchema=' + encodeURIComponent($('source-schema').value) +
            '&targetSchema=' + encodeURIComponent($('target-schema').value);
        busy($('tables-suggest'), post(url)).then(function (list) {
            var added = 0;
            list.forEach(function (s) {
                var note = 'Suggested by name \u00b7 ' + Math.round(s.score) + '% \u00b7 ' + (s.reasons || []).join('; ');
                if (addPair(s.sourceTable, s.targetTable, note)) { added++; }
            });
            renderPairsList();
            DataSphere.notify(added ? 'success' : 'info', added
                ? added + ' table pair(s) suggested. Review them before saving.'
                : 'No clear table matches were found. Choose the pairs by hand.');
        }).catch(function (e) { showError($('tables-error'), e); });
    });

    function renderPairsList() {
        var body = $('pairs-body');
        clear(body);
        $('pairs-empty').hidden = pendingPairs.length > 0;
        pendingPairs.forEach(function (p, index) {
            var tr = el('tr');
            tr.appendChild(el('td', 'fw-semibold', keyOf(p.source.schema, p.source.name)));
            tr.appendChild(el('td', 'fw-semibold', keyOf(p.target.schema, p.target.name)));
            tr.appendChild(el('td', 'ds-muted', p.note));
            var td = el('td', 'text-end');
            var rm = el('button', 'btn btn-sm btn-outline-secondary');
            rm.type = 'button';
            rm.appendChild(icon('bi-x-lg'));
            rm.setAttribute('aria-label', 'Remove this pair');
            rm.addEventListener('click', function () { pendingPairs.splice(index, 1); renderPairsList(); });
            td.appendChild(rm);
            tr.appendChild(td);
            body.appendChild(tr);
        });
        $('tables-save').disabled = pendingPairs.length === 0;
    }

    $('tables-save').addEventListener('click', function () {
        $('tables-error').hidden = true;
        var go = function () {
            var payload = {
                pairs: pendingPairs.map(function (p) { return { source: p.source, target: p.target }; })
            };
            busy($('tables-save'), DataSphere.fetchJson(PROJECT_URL + '/tables', { method: 'PUT', json: payload }))
                .then(function (p) {
                    pendingPairs = [];
                    setProject(p);
                    loadPendingFromProject();
                    DataSphere.notify('success', 'Table structure read. Generate the mapping suggestions next.');
                    showStep('mapping');
                }).catch(function (e) { showError($('tables-error'), e); });
        };
        if (project.capabilities.canEditMapping || project.approval) {
            DataSphere.confirm({
                title: 'Replace the current selection?',
                message: 'Saving a new selection discards the existing mapping and approval for this project.',
                confirmLabel: 'Replace selection', style: 'danger'
            }).then(function (ok) { if (ok) { go(); } });
        } else {
            go();
        }
    });

    /* ------------------------------------------------------------------ 3. mapping */

    function isMapped(m) { return m.targetColumn !== null && m.targetColumn !== undefined && m.targetColumn !== ''; }

    function awaiting(m) { return m.status === 'SUGGESTED' || m.status === 'NEEDS_REVIEW' || m.status === 'AMBIGUOUS'; }

    function mappingCounts() {
        var c = { total: 0, mapped: 0, awaiting: 0, unmapped: 0, accepted: 0 };
        (project ? project.tablePairs : []).forEach(function (pair) {
            pair.mappings.forEach(function (m) {
                c.total++;
                if (isMapped(m)) { c.mapped++; } else { c.unmapped++; }
                if (awaiting(m)) { c.awaiting++; }
                if (m.status === 'ACCEPTED' || m.status === 'MODIFIED') { c.accepted++; }
            });
        });
        return c;
    }

    function hasUserDecisions() {
        return project.tablePairs.some(function (pair) {
            return pair.mappings.some(function (m) {
                return m.status === 'ACCEPTED' || m.status === 'REJECTED' || m.status === 'MODIFIED';
            });
        });
    }

    function renderMapping() {
        var hasPairs = project.tablePairs.length > 0;
        $('mapping-empty').hidden = hasPairs;
        $('mapping-body').hidden = !hasPairs;
        if (!hasPairs) { return; }
        var y = window.scrollY;
        var host = $('mapping-pairs');
        clear(host);
        var c = mappingCounts();
        $('mapping-summary').textContent = c.total
            ? c.mapped + ' of ' + c.total + ' columns mapped \u00b7 ' + c.awaiting + ' waiting for your decision \u00b7 ' +
              c.unmapped + ' unmapped'
            : 'No suggestions yet.';
        $('mapping-accept-all').disabled = !project.capabilities.canEditMapping;
        project.tablePairs.forEach(function (pair) { host.appendChild(renderPair(pair)); });
        window.scrollTo(0, y);
    }

    function filterOf(pair) {
        if (!filters[pair.id]) { filters[pair.id] = { text: '', status: 'ALL' }; }
        return filters[pair.id];
    }

    function colInfo(columns, name) {
        for (var i = 0; i < columns.length; i++) { if (columns[i].name === name) { return columns[i]; } }
        return null;
    }

    function renderPair(pair) {
        var card = el('section', 'ds-card ds-pair');
        card.setAttribute('aria-label', 'Mapping of ' + pair.sourceTable + ' to ' + pair.targetTable);
        var header = el('div', 'ds-card-header');
        var title = el('h3', 'ds-card-title');
        title.textContent = keyOf(pair.sourceSchema, pair.sourceTable) + '  \u2192  ' + keyOf(pair.targetSchema, pair.targetTable);
        header.appendChild(title);
        var mappedCount = pair.mappings.filter(isMapped).length;
        header.appendChild(el('span', 'ds-chip ds-chip-muted', mappedCount + ' / ' + pair.mappings.length + ' mapped'));
        card.appendChild(header);

        var body = el('div', 'ds-card-body');
        var meta = el('div', 'ds-pair-meta');
        var addMeta = function (label, value) {
            var d = el('div');
            d.appendChild(document.createTextNode(label + ' '));
            d.appendChild(el('strong', null, value));
            meta.appendChild(d);
        };
        addMeta('Source columns:', String(pair.sourceColumns.length));
        addMeta('Target columns:', String(pair.targetColumns.length));
        addMeta('Source primary key:', keyList(pair.sourceColumns));
        addMeta('Target primary key:', keyList(pair.targetColumns));
        body.appendChild(meta);

        body.appendChild(renderToolbar(pair));

        if (!pair.mappings.length) {
            body.appendChild(el('div', 'ds-muted', 'No suggestions yet. Click "Generate mapping suggestions".'));
            card.appendChild(body);
            return card;
        }

        var used = {};
        pair.mappings.forEach(function (m) {
            if (isMapped(m)) { (used[m.targetColumn] = used[m.targetColumn] || []).push(m.sourceColumn); }
        });

        var wrap = el('div', 'table-responsive');
        var table = el('table', 'table ds-table ds-map-table align-middle');
        var thead = el('thead');
        var hr = el('tr');
        ['Source column', 'Source type', 'Suggested target', 'Target type', 'Score', 'Reason', 'Status', 'Decision']
            .forEach(function (h) { var th = el('th', null, h); th.scope = 'col'; hr.appendChild(th); });
        thead.appendChild(hr);
        table.appendChild(thead);

        var tbody = el('tbody');
        var f = filterOf(pair);
        var shown = 0;
        pair.mappings.forEach(function (m) {
            if (!passesFilter(m, f, used)) { return; }
            shown++;
            tbody.appendChild(renderRow(pair, m, used));
        });
        if (!shown) {
            var tr = el('tr');
            var td = el('td', 'ds-muted', 'No columns match the current filter.');
            td.colSpan = 8;
            tr.appendChild(td);
            tbody.appendChild(tr);
        }
        table.appendChild(tbody);
        wrap.appendChild(table);
        body.appendChild(wrap);
        body.appendChild(renderUnmapped(pair, used));
        card.appendChild(body);
        return card;
    }

    function keyList(columns) {
        var keys = columns.filter(function (c) { return c.primaryKey; }).map(function (c) { return c.name; });
        return keys.length ? keys.join(', ') : 'none';
    }

    function passesFilter(m, f, used) {
        if (f.text) {
            var t = f.text.toLowerCase();
            if ((m.sourceColumn || '').toLowerCase().indexOf(t) < 0 && (m.targetColumn || '').toLowerCase().indexOf(t) < 0) {
                return false;
            }
        }
        switch (f.status) {
            case 'DECIDE': return awaiting(m);
            case 'MAPPED': return isMapped(m);
            case 'UNMAPPED': return !isMapped(m);
            case 'WARN': return (m.warnings && m.warnings.length > 0) ||
                (isMapped(m) && used[m.targetColumn] && used[m.targetColumn].length > 1);
            default: return true;
        }
    }

    function renderToolbar(pair) {
        var bar = el('div', 'ds-map-toolbar');
        var f = filterOf(pair);

        var search = el('input', 'form-control form-control-sm');
        search.type = 'search';
        search.placeholder = 'Search columns';
        search.value = f.text;
        search.setAttribute('aria-label', 'Search columns of ' + pair.sourceTable);
        search.addEventListener('input', function () {
            f.text = search.value;
            renderMappingKeepingFocus(pair.id, search);
        });
        bar.appendChild(search);

        var status = el('select', 'form-select form-select-sm');
        status.setAttribute('aria-label', 'Filter columns');
        [['ALL', 'All columns'], ['DECIDE', 'Waiting for my decision'], ['MAPPED', 'Mapped'], ['UNMAPPED', 'Unmapped'],
            ['WARN', 'With warnings or duplicates']].forEach(function (o) {
            var opt = el('option', null, o[1]);
            opt.value = o[0];
            status.appendChild(opt);
        });
        status.value = f.status;
        status.addEventListener('change', function () { f.status = status.value; renderMapping(); });
        bar.appendChild(status);

        var accept = el('button', 'btn btn-sm btn-outline-primary');
        accept.type = 'button';
        accept.textContent = 'Accept clean suggestions';
        accept.disabled = !project.capabilities.canEditMapping;
        accept.addEventListener('click', function () {
            sendChanges([{ action: 'ACCEPT_ALL_SUGGESTED', pairId: pair.id }]);
        });
        bar.appendChild(accept);

        var reset = el('button', 'btn btn-sm btn-outline-secondary');
        reset.type = 'button';
        reset.textContent = 'Reset this table to suggestions';
        reset.disabled = !project.capabilities.canEditMapping;
        reset.addEventListener('click', function () {
            DataSphere.confirm({
                title: 'Reset this table?',
                message: 'Your decisions for ' + pair.sourceTable + ' are discarded and the suggestions are recalculated.',
                confirmLabel: 'Reset'
            }).then(function (ok) { if (ok) { sendChanges([{ action: 'RESET_PAIR', pairId: pair.id }]); } });
        });
        bar.appendChild(reset);
        return bar;
    }

    /** Re-render while typing in a search box without losing the caret. */
    function renderMappingKeepingFocus(pairId, input) {
        var pos = input.selectionStart;
        renderMapping();
        var inputs = document.querySelectorAll('#mapping-pairs input[type="search"]');
        for (var i = 0; i < inputs.length; i++) {
            if (inputs[i].getAttribute('aria-label') === input.getAttribute('aria-label')) {
                inputs[i].focus();
                try { inputs[i].setSelectionRange(pos, pos); } catch (e) { /* ignore */ }
            }
        }
    }

    function typeCell(info) {
        var td = el('td');
        if (!info) { td.textContent = '\u2014'; return td; }
        td.appendChild(el('div', 'ds-col-type', info.type + (info.nullable ? '' : ' \u00b7 NOT NULL')));
        return td;
    }

    function renderRow(pair, m, used) {
        var src = colInfo(pair.sourceColumns, m.sourceColumn);
        var tgt = isMapped(m) ? colInfo(pair.targetColumns, m.targetColumn) : null;
        var duplicate = isMapped(m) && used[m.targetColumn] && used[m.targetColumn].length > 1;
        var tr = el('tr');
        if (duplicate || m.compatibility === 'INCOMPATIBLE') { tr.className = 'ds-row-error'; }
        else if (awaiting(m) || (m.warnings && m.warnings.length)) { tr.className = 'ds-row-attention'; }
        else if (!isMapped(m)) { tr.className = 'ds-row-unmapped'; }

        // Source column
        var c1 = el('td');
        var nameLine = el('div', 'ds-col-name', m.sourceColumn);
        if (src && src.primaryKey) { nameLine.appendChild(el('span', 'ds-key', 'PK')); }
        if (src && src.foreignKey) { nameLine.appendChild(el('span', 'ds-key', 'FK')); }
        if (src && src.unique && !src.primaryKey) { nameLine.appendChild(el('span', 'ds-key', 'UQ')); }
        c1.appendChild(nameLine);
        tr.appendChild(c1);
        tr.appendChild(typeCell(src));

        // Suggested target (dropdown) and conversion
        var c3 = el('td');
        c3.appendChild(targetSelect(pair, m, used));
        if (isMapped(m)) { c3.appendChild(conversionControls(pair, m, src, tgt)); }
        tr.appendChild(c3);
        tr.appendChild(typeCell(tgt));

        // Score
        var c5 = el('td');
        c5.appendChild(scoreBadge(m));
        tr.appendChild(c5);

        // Reason
        var c6 = el('td');
        if (m.reasons && m.reasons.length) {
            var ul = el('ul', 'ds-map-reasons');
            m.reasons.forEach(function (r) { ul.appendChild(el('li', null, r)); });
            c6.appendChild(ul);
        }
        var warns = (m.warnings || []).slice();
        if (duplicate) {
            var others = used[m.targetColumn].filter(function (s) { return s !== m.sourceColumn; });
            warns.push('The target column ' + m.targetColumn + ' is also used by ' + others.join(', ') +
                '. Each target column can receive only one source column.');
        }
        if (warns.length) {
            var wl = el('ul', 'ds-map-warnings');
            warns.forEach(function (w) { wl.appendChild(el('li', null, w)); });
            c6.appendChild(wl);
        }
        if (m.status === 'AMBIGUOUS' && m.candidates && m.candidates.length) {
            c6.appendChild(el('div', 'ds-muted', 'Close candidates: ' + m.candidates.slice(0, 4).map(function (k) {
                return k.targetColumn + ' (' + Math.round(k.score) + ')';
            }).join(', ')));
        }
        tr.appendChild(c6);

        // Status
        var c7 = el('td');
        c7.appendChild(el('span', 'ds-chip ' + (STATUS_CHIP[m.status] || 'ds-chip-muted'), STATUS_LABEL[m.status] || m.status));
        tr.appendChild(c7);

        // Decision buttons
        var c8 = el('td');
        var actions = el('div', 'ds-map-actions');
        var editable = project.capabilities.canEditMapping;
        var mapped = isMapped(m);
        actions.appendChild(actionButton('bi-check-lg', 'Accept mapping of ' + m.sourceColumn, 'btn-outline-success',
            !editable || !mapped || m.status === 'ACCEPTED' || m.compatibility === 'INCOMPATIBLE',
            function () { sendChanges([{ action: 'ACCEPT', pairId: pair.id, sourceColumn: m.sourceColumn }]); }));
        actions.appendChild(actionButton('bi-x-lg', 'Reject suggestion for ' + m.sourceColumn, 'btn-outline-danger',
            !editable || !mapped,
            function () { sendChanges([{ action: 'REJECT', pairId: pair.id, sourceColumn: m.sourceColumn }]); }));
        actions.appendChild(actionButton('bi-eraser', 'Leave ' + m.sourceColumn + ' unmapped', 'btn-outline-secondary',
            !editable || !mapped,
            function () { sendChanges([{ action: 'CLEAR', pairId: pair.id, sourceColumn: m.sourceColumn }]); }));
        c8.appendChild(actions);
        tr.appendChild(c8);
        return tr;
    }

    function actionButton(iconName, label, style, disabled, handler) {
        var b = el('button', 'btn btn-sm ' + style);
        b.type = 'button';
        b.appendChild(icon(iconName));
        b.setAttribute('aria-label', label);
        b.title = label;
        b.disabled = disabled;
        b.addEventListener('click', handler);
        return b;
    }

    function scoreBadge(m) {
        var wrap = el('div', 'ds-score ' + (isMapped(m) ? 'ds-score-' + (CONFIDENCE_CLASS[m.confidence] || 'weak') : 'ds-score-none'));
        if (!isMapped(m)) {
            wrap.appendChild(el('span', 'ds-score-num', '\u2014'));
            return wrap;
        }
        wrap.appendChild(el('span', 'ds-score-num', String(Math.round(m.score))));
        wrap.appendChild(el('span', 'ds-score-label', CONFIDENCE_LABEL[m.confidence] || ''));
        wrap.title = 'Rule-of-thumb score from 0 to 100. It is not a probability.';
        return wrap;
    }

    function targetSelect(pair, m, used) {
        var select = el('select', 'form-select form-select-sm');
        select.setAttribute('aria-label', 'Target column for ' + m.sourceColumn);
        select.disabled = !project.capabilities.canEditMapping;

        var none = el('option', null, '\u2014 not mapped \u2014');
        none.value = '';
        select.appendChild(none);

        var scoreByName = {};
        (m.candidates || []).forEach(function (k) { scoreByName[k.targetColumn] = k; });
        var best = el('optgroup');
        best.label = 'Best matches';
        var others = el('optgroup');
        others.label = 'Other target columns';
        pair.targetColumns.forEach(function (t) {
            var cand = scoreByName[t.name];
            var label = t.name + '  \u00b7  ' + t.type;
            var usedBy = used[t.name] && used[t.name].filter(function (s) { return s !== m.sourceColumn; });
            if (usedBy && usedBy.length) { label += '  (used by ' + usedBy.join(', ') + ')'; }
            if (cand) { label += '  [' + Math.round(cand.score) + ']'; }
            var o = el('option', null, label);
            o.value = t.name;
            (cand ? best : others).appendChild(o);
        });
        if (best.children.length) { select.appendChild(best); }
        if (others.children.length) { select.appendChild(others); }
        select.value = isMapped(m) ? m.targetColumn : '';
        select.addEventListener('change', function () {
            sendChanges([{ action: select.value ? 'SET_TARGET' : 'CLEAR', pairId: pair.id,
                sourceColumn: m.sourceColumn, targetColumn: select.value || null }]);
        });
        return select;
    }

    function applicableConversions(srcCat, tgtCat) {
        var key = srcCat + '|' + tgtCat;
        if (!transformCache[key]) {
            transformCache[key] = DataSphere.fetchJson('/api/mapping/transformations?source=' +
                encodeURIComponent(srcCat) + '&target=' + encodeURIComponent(tgtCat)).catch(function () {
                delete transformCache[key];
                return [];
            });
        }
        return transformCache[key];
    }

    function conversionControls(pair, m, src, tgt) {
        var box = el('div', 'ds-map-conv');
        if (!src || !tgt || !project.capabilities.canEditMapping) {
            if (m.transformation && m.transformation !== 'NONE') {
                box.appendChild(el('span', 'ds-muted', 'Conversion: ' + m.transformation));
            }
            return box;
        }
        var label = el('span', 'ds-muted', 'Conversion');
        var select = el('select', 'form-select form-select-sm');
        select.setAttribute('aria-label', 'Conversion for ' + m.sourceColumn);
        var param = el('input', 'form-control form-control-sm');
        param.type = 'text';
        param.maxLength = 100;
        param.setAttribute('aria-label', 'Conversion value for ' + m.sourceColumn);
        param.value = m.transformationParameter || '';
        param.hidden = true;
        var apply = el('button', 'btn btn-sm btn-outline-primary', 'Apply');
        apply.type = 'button';
        apply.hidden = true;
        box.append(label, select, param, apply);

        var options = {};
        var current = el('option', null, 'No conversion');
        current.value = 'NONE';
        select.appendChild(current);
        select.value = 'NONE';

        function send() {
            sendChanges([{ action: 'SET_TRANSFORMATION', pairId: pair.id, sourceColumn: m.sourceColumn,
                transformation: select.value, transformationParameter: param.value.trim() || null }]);
        }
        function syncParam() {
            var t = options[select.value];
            var needsInput = !!(t && t.parameterAllowed);
            param.hidden = !needsInput;
            apply.hidden = !needsInput;
            param.placeholder = t && t.parameterRequired ? 'Value to use' : 'Optional, e.g. yyyy-MM-dd';
        }
        select.addEventListener('change', function () {
            syncParam();
            if (!options[select.value] || !options[select.value].parameterAllowed) { send(); }
        });
        apply.addEventListener('click', send);

        applicableConversions(src.category, tgt.category).then(function (list) {
            list.forEach(function (t) {
                options[t.name] = t;
                if (t.name === 'NONE') { return; }
                var o = el('option', null, t.label);
                o.value = t.name;
                o.title = t.description;
                select.appendChild(o);
            });
            select.value = m.transformation && options[m.transformation] ? m.transformation : 'NONE';
            syncParam();
            if (list.length <= 1) { box.hidden = true; }
        });
        return box;
    }

    function renderUnmapped(pair, used) {
        var wrap = el('div', 'mt-3');
        var unmappedSource = pair.mappings.filter(function (m) { return !isMapped(m); });
        var unmappedTarget = pair.targetColumns.filter(function (t) { return !used[t.name]; });

        var h1 = el('div', 'ds-h3', 'Source columns that will not be migrated (' + unmappedSource.length + ')');
        wrap.appendChild(h1);
        if (unmappedSource.length) {
            var l1 = el('ul', 'ds-unmapped-list');
            unmappedSource.forEach(function (m) { l1.appendChild(el('li', null, m.sourceColumn)); });
            wrap.appendChild(l1);
        } else {
            wrap.appendChild(el('div', 'ds-muted', 'Every source column has a target.'));
        }

        var h2 = el('div', 'ds-h3 mt-3', 'Target columns that receive no data (' + unmappedTarget.length + ')');
        wrap.appendChild(h2);
        if (unmappedTarget.length) {
            var l2 = el('ul', 'ds-unmapped-list');
            unmappedTarget.forEach(function (t) {
                var li = el('li', null, t.name + (t.required ? ' \u2014 required, must be mapped' : ''));
                if (t.required) { li.className = 'text-danger fw-semibold'; }
                l2.appendChild(li);
            });
            wrap.appendChild(l2);
        } else {
            wrap.appendChild(el('div', 'ds-muted', 'Every target column receives data.'));
        }
        return wrap;
    }

    function sendChanges(changes) {
        return DataSphere.fetchJson(MAPPING_URL, {
            method: 'PUT', json: { expectedVersion: project.mappingVersion, changes: changes }
        }).then(function (p) {
            setProject(p);
            if (p.state === 'MAPPING_MODIFIED') {
                // Keep the user informed that earlier validation/approval no longer applies.
                $('approve-hint').textContent = 'The mapping changed. Validate it and approve it again.';
            }
        }).catch(function (e) {
            toastError(e);
            if (e.details && e.details.code === 'PRJ-CONFLICT-001') { load(); }
        });
    }

    $('mapping-suggest').addEventListener('click', function () {
        var run = function (overwrite) {
            busy($('mapping-suggest'), post(MAPPING_URL + '/suggest', { overwrite: overwrite })).then(function (p) {
                setProject(p);
                DataSphere.notify('success', 'Suggestions generated. Review every mapping before approving.');
            }).catch(toastError);
        };
        if (hasUserDecisions()) {
            DataSphere.confirm({
                title: 'Replace your decisions?',
                message: 'Generating suggestions again discards the mappings you accepted, changed or rejected, ' +
                    'and withdraws any approval.',
                confirmLabel: 'Generate again', style: 'danger'
            }).then(function (ok) { if (ok) { run(true); } });
        } else {
            run(project.approval ? true : false);
        }
    });

    $('mapping-accept-all').addEventListener('click', function () {
        sendChanges([{ action: 'ACCEPT_ALL_SUGGESTED' }]);
    });

    /* ------------------------------------------------------------------ 4. validate and approve */

    function severityIcon(sev) {
        return sev === 'ERROR' ? 'bi-x-octagon-fill' : sev === 'WARNING' ? 'bi-exclamation-triangle-fill' : 'bi-info-circle-fill';
    }

    function renderReport(host, report) {
        clear(host);
        if (!report) { return; }
        var summary = el('div', 'd-flex flex-wrap gap-2 mb-2');
        summary.appendChild(el('span', 'ds-chip ' + (report.valid ? 'ds-chip-success' : 'ds-chip-danger'),
            report.valid ? 'Validation passed' : 'Validation failed'));
        summary.appendChild(el('span', 'ds-chip ds-chip-muted', report.errors + ' error(s)'));
        summary.appendChild(el('span', 'ds-chip ds-chip-muted', report.warnings + ' warning(s)'));
        summary.appendChild(el('span', 'ds-chip ds-chip-muted', report.infos + ' note(s)'));
        host.appendChild(summary);
        var list = el('ul', 'ds-issues');
        ['ERROR', 'WARNING', 'INFO'].forEach(function (sev) {
            (report.issues || []).filter(function (i) { return i.severity === sev; }).forEach(function (i) {
                var li = el('li', 'ds-issue ds-issue-' + sev.toLowerCase());
                li.appendChild(icon(severityIcon(sev)));
                var text = el('div');
                text.appendChild(document.createTextNode(i.message));
                text.appendChild(el('span', 'ds-issue-code', i.code));
                li.appendChild(text);
                list.appendChild(li);
            });
        });
        host.appendChild(list);
    }

    function renderApprove() {
        var host = $('validation-result');
        if (project.validation && project.validationIsCurrent) {
            renderReport(host, project.validation);
        } else {
            clear(host);
            if (project.capabilities.canValidate) {
                host.appendChild(el('div', 'ds-callout ds-callout-warning',
                    project.validation ? 'The mapping changed since the last validation. Validate it again.'
                        : 'The mapping has not been validated yet.'));
            }
        }

        var cur = $('approval-current');
        if (project.approval) {
            cur.hidden = false;
            $('approval-current-text').textContent = 'Approved by ' + project.approval.approvedBy + ' on ' +
                formatTime(project.approval.approvedAt) + ' (mapping version ' + project.approval.mappingVersion +
                '). Any change to the mapping, or to either database\'s structure, withdraws this approval.';
        } else {
            cur.hidden = true;
        }

        $('validate-run').disabled = !project.capabilities.canValidate;
        var can = project.capabilities.canApprove;
        $('approve-confirm').disabled = !can;
        if (!can) { $('approve-confirm').checked = false; }
        $('approve-run').disabled = !can || !$('approve-confirm').checked;

        var hint = '';
        if (project.approval) { hint = 'This mapping is approved. Continue to the preview.'; }
        else if (!project.capabilities.canValidate) { hint = 'Select tables and generate mapping suggestions first.'; }
        else if (!project.validation || !project.validationIsCurrent) { hint = 'Run the validation first.'; }
        else if (!project.validation.valid) { hint = 'Fix the errors above (step 3), then validate again.'; }
        else { hint = 'Tick the box to confirm your review, then approve.'; }
        $('approve-hint').textContent = hint;
    }

    $('approve-confirm').addEventListener('change', function () {
        $('approve-run').disabled = !project.capabilities.canApprove || !$('approve-confirm').checked;
    });

    $('validate-run').addEventListener('click', function () {
        busy($('validate-run'), post(MAPPING_URL + '/validate')).then(function (res) {
            setProject(res.project);
            DataSphere.notify(res.report.valid ? 'success' : 'warning', res.report.valid
                ? 'Validation passed with ' + res.report.warnings + ' warning(s).'
                : 'Validation found ' + res.report.errors + ' error(s).');
        }).catch(toastError);
    });

    $('approve-run').addEventListener('click', function () {
        DataSphere.confirm({
            title: 'Approve this mapping?',
            message: 'You confirm that you reviewed the mapping. Approval enables the migration preview. ' +
                'It does not start a migration.',
            confirmLabel: 'Approve Mapping & Continue'
        }).then(function (ok) {
            if (!ok) { return; }
            busy($('approve-run'), post(MAPPING_URL + '/approve', {
                expectedVersion: project.mappingVersion, confirmReviewed: $('approve-confirm').checked
            })).then(function (p) {
                $('approve-confirm').checked = false;
                setProject(p);
                DataSphere.notify('success', 'Mapping approved. You can now run the migration preview.');
                showStep('preview');
            }).catch(function (e) {
                toastError(e);
                load();
            });
        });
    });

    /* ------------------------------------------------------------------ 5. preview */

    function renderPreview() {
        $('preview-run').disabled = !project.capabilities.canPreview;
        $('migration-start').disabled = !project.capabilities.canStart;
        $('migration-cancel').disabled = !project.capabilities.canCancel;
        $('migration-validate').disabled = !project.capabilities.canReconcile;
        var hint;
        if (!project.capabilities.canPreview) {
            hint = 'Approve the mapping first (step 4). The preview and the start button stay disabled until then.';
        } else if (project.capabilities.canCancel) {
            hint = 'A migration is running. You can cancel at the next batch boundary. Committed batches stay.';
        } else if (!project.capabilities.canStart) {
            hint = 'Run the preview. It re-checks the approval and the databases before anything can start.';
        } else {
            hint = 'The preview passed on ' + formatTime(project.previewVerifiedAt) +
                '. Start copies rows with INSERT only. It never drops, truncates or overwrites target data.';
        }
        $('preview-hint').textContent = hint;
        if (project.lastRun) {
            renderRun(project.lastRun);
        }
    }

    function renderPreviewResult(preview) {
        var host = $('preview-result');
        clear(host);
        var notes = el('ul', 'ds-list');
        preview.notes.forEach(function (n) { notes.appendChild(el('li', null, n)); });
        host.appendChild(notes);
        host.appendChild(el('div', 'ds-muted mb-3', 'From ' + preview.sourceDatabase + ' to ' + preview.targetDatabase +
            ' \u00b7 mapping version ' + preview.mappingVersion + ' \u00b7 checked ' + formatTime(preview.generatedAt)));

        preview.pairs.forEach(function (p) {
            var card = el('section', 'ds-card ds-pair');
            var head = el('div', 'ds-card-header');
            head.appendChild(el('h3', 'ds-card-title', p.sourceTable + '  \u2192  ' + p.targetTable));
            head.appendChild(el('span', 'ds-chip ds-chip-info', p.sourceRowCount.toLocaleString() + ' source rows'));
            card.appendChild(head);
            var body = el('div', 'ds-card-body');

            var wrap = el('div', 'table-responsive');
            var table = el('table', 'table ds-table align-middle');
            var hr = el('tr');
            ['Source column', 'Source type', 'Target column', 'Target type', 'Conversion', 'Score'].forEach(function (h) {
                var th = el('th', null, h); th.scope = 'col'; hr.appendChild(th);
            });
            var thead = el('thead'); thead.appendChild(hr); table.appendChild(thead);
            var tbody = el('tbody');
            p.columns.forEach(function (c) {
                var tr = el('tr');
                [c.sourceColumn, c.sourceType, c.targetColumn, c.targetType, c.transformation, String(Math.round(c.score))]
                    .forEach(function (v) { tr.appendChild(el('td', null, v)); });
                tbody.appendChild(tr);
            });
            table.appendChild(tbody);
            wrap.appendChild(table);
            body.appendChild(wrap);

            body.appendChild(namedList('Source columns not migrated', p.unmappedSourceColumns, 'None.'));
            body.appendChild(namedList('Target columns that stay empty', p.unmappedTargetColumns, 'None.'));
            if (p.warnings.length) {
                body.appendChild(el('div', 'ds-h3 mt-3', 'Warnings'));
                var wl = el('ul', 'ds-map-warnings');
                p.warnings.forEach(function (w) { wl.appendChild(el('li', null, w)); });
                body.appendChild(wl);
            }
            body.appendChild(el('div', 'ds-h3 mt-3', 'INSERT shape (for review only, not executed)'));
            body.appendChild(el('pre', 'ds-sql', p.statementPreview));
            if (p.createTableSql) {
                body.appendChild(el('div', 'ds-h3 mt-3', 'CREATE TABLE (generated, not executed)'));
                body.appendChild(el('pre', 'ds-sql', p.createTableSql));
            }
            card.appendChild(body);
            host.appendChild(card);
        });
    }

    function namedList(title, items, emptyText) {
        var wrap = el('div', 'mt-3');
        wrap.appendChild(el('div', 'ds-h3', title + ' (' + items.length + ')'));
        if (items.length) {
            var ul = el('ul', 'ds-unmapped-list');
            items.forEach(function (i) { ul.appendChild(el('li', null, i)); });
            wrap.appendChild(ul);
        } else {
            wrap.appendChild(el('div', 'ds-muted', emptyText));
        }
        return wrap;
    }

    $('preview-run').addEventListener('click', function () {
        busy($('preview-run'), post('/api/migration/' + encodeURIComponent(projectId) + '/preview')).then(function (preview) {
            renderPreviewResult(preview);
            return load();
        }).then(function () {
            DataSphere.notify('success', 'Preview complete. Nothing was written to either database.');
        }).catch(function (e) {
            toastError(e);
            var box = $('preview-result');
            clear(box);
            var err = el('div', 'ds-inline-error');
            err.textContent = describe(e).join('\n');
            box.appendChild(err);
            load();
        });
    });

    function renderRun(run) {
        var host = $('preview-result');
        var box = el('div', 'ds-callout ds-callout-info mb-3');
        var title = el('div', null, 'Last run: ' + run.status + ' · inserted ' + (run.rowsInserted || 0) +
            ' · failed ' + (run.rowsFailed || 0) + ' · batch ' + run.batchSize);
        box.appendChild(title);
        host.insertBefore(box, host.firstChild);
    }

    $('migration-cancel').addEventListener('click', function () {
        post('/api/migration/' + encodeURIComponent(projectId) + '/cancel').then(function () {
            DataSphere.notify('warning', 'Cancel requested. The job stops at the next batch.');
        }).catch(toastError);
    });

    $('migration-validate').addEventListener('click', function () {
        busy($('migration-validate'), post('/api/migration/' + encodeURIComponent(projectId) + '/validate'))
            .then(function (report) {
                DataSphere.notify(report.overall === 'FAILED' ? 'error' : 'info',
                    'Validation ' + report.overall + '. Counts matching do not prove values are identical.');
                load();
            }).catch(toastError);
    });

    $('migration-start').addEventListener('click', function () {
        DataSphere.confirm({
            title: 'Start the migration?',
            message: 'The server re-checks the approval, then INSERTs rows into the target in batches. ' +
                'It will not drop, truncate or overwrite existing target rows. Committed batches are not rolled back if a later batch fails.',
            confirmLabel: 'Insert into target'
        }).then(function (ok) {
            if (!ok) { return; }
            busy($('migration-start'), post('/api/migration/' + encodeURIComponent(projectId) + '/start', {
                confirmWrite: true, batchSize: 1000, insertMode: 'FAIL_ON_DUPLICATE'
            })).then(function (run) {
                DataSphere.notify(run.status === 'COMPLETED' ? 'success' : 'warning',
                    'Migration ' + run.status + '. Inserted ' + (run.rowsInserted || 0) + ' row(s).');
                load();
            }).catch(function (e) {
                var box = $('preview-result');
                var err = el('div', 'ds-inline-error mb-3');
                err.textContent = describe(e).join('\n');
                box.insertBefore(err, box.firstChild);
                toastError(e);
                load();
            });
        });
    });

    /* ------------------------------------------------------------------ start */

    load().then(function () {
        loadPendingFromProject();
        showStep(firstIncompleteStep());
    }).catch(function (e) {
        DataSphere.notify('error', e.message);
    });
})();
