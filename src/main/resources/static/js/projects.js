/*
 * Project list page. All text is inserted with textContent, never as HTML.
 */
(function () {
    'use strict';

    var body = document.getElementById('projects-body');
    var wrap = document.getElementById('projects-table-wrap');
    var empty = document.getElementById('projects-empty');
    var form = document.getElementById('new-project-form');
    var nameInput = document.getElementById('new-project-name');
    var errorBox = document.getElementById('new-project-error');

    function cell(text, className) {
        var td = document.createElement('td');
        td.textContent = text == null || text === '' ? '\u2014' : text;
        if (className) { td.className = className; }
        return td;
    }

    function formatTime(iso) {
        if (!iso) { return ''; }
        var d = new Date(iso);
        return isNaN(d.getTime()) ? '' : d.toLocaleString();
    }

    function render(projects) {
        body.textContent = '';
        empty.hidden = projects.length > 0;
        wrap.hidden = projects.length === 0;
        projects.sort(function (a, b) { return (b.updatedAt || '').localeCompare(a.updatedAt || ''); });
        projects.forEach(function (p) {
            var tr = document.createElement('tr');
            var nameCell = document.createElement('td');
            var link = document.createElement('a');
            link.href = '/projects/' + encodeURIComponent(p.id);
            link.textContent = p.name;
            link.className = 'fw-semibold';
            nameCell.appendChild(link);
            tr.appendChild(nameCell);
            tr.appendChild(cell(p.sourceDatabase));
            tr.appendChild(cell(p.targetDatabase));
            tr.appendChild(cell(String(p.tablePairs)));

            var statusCell = document.createElement('td');
            var chip = document.createElement('span');
            chip.className = 'ds-chip ' + (p.approved ? 'ds-chip-success' : 'ds-chip-muted');
            chip.textContent = p.stateLabel;
            statusCell.appendChild(chip);
            tr.appendChild(statusCell);

            tr.appendChild(cell(formatTime(p.updatedAt), 'text-nowrap'));

            var actions = document.createElement('td');
            actions.className = 'text-end text-nowrap';
            var open = document.createElement('a');
            open.className = 'btn btn-sm btn-outline-primary me-1';
            open.href = link.href;
            open.textContent = 'Open';
            var del = document.createElement('button');
            del.type = 'button';
            del.className = 'btn btn-sm btn-outline-danger';
            del.textContent = 'Delete';
            del.setAttribute('aria-label', 'Delete project ' + p.name);
            del.addEventListener('click', function () { remove(p); });
            actions.append(open, del);
            tr.appendChild(actions);
            body.appendChild(tr);
        });
    }

    function load() {
        DataSphere.fetchJson('/api/projects').then(render).catch(function (e) {
            DataSphere.notify('error', e.message);
        });
    }

    function remove(project) {
        DataSphere.confirm({
            title: 'Delete project',
            message: 'Delete "' + project.name + '"? Its mapping and approval are removed. ' +
                'Your databases are not touched.',
            confirmLabel: 'Delete project',
            style: 'danger'
        }).then(function (ok) {
            if (!ok) { return; }
            DataSphere.fetchJson('/api/projects/' + encodeURIComponent(project.id), { method: 'DELETE' })
                .then(function () {
                    DataSphere.notify('success', 'Project deleted.');
                    load();
                })
                .catch(function (e) { DataSphere.notify('error', e.message); });
        });
    }

    form.addEventListener('submit', function (e) {
        e.preventDefault();
        errorBox.hidden = true;
        var name = nameInput.value.trim();
        if (!name) {
            errorBox.textContent = 'Enter a project name.';
            errorBox.hidden = false;
            return;
        }
        DataSphere.fetchJson('/api/projects', { method: 'POST', json: { name: name } })
            .then(function (project) {
                window.location.href = '/projects/' + encodeURIComponent(project.id);
            })
            .catch(function (err) {
                var issues = err.details && err.details.fieldIssues;
                errorBox.textContent = issues && issues.length ? issues[0].message : err.message;
                errorBox.hidden = false;
            });
    });

    load();
})();
