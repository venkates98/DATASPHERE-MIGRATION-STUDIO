(function () {
    'use strict';

    function mark(ok) {
        return ok ? 'Yes' : 'No';
    }

    DataSphere.fetchJson('/api/compatibility').then(function (rows) {
        var body = document.querySelector('#compat-table tbody');
        rows.forEach(function (row) {
            var tr = document.createElement('tr');
            [row.sourceLabel, row.targetLabel, row.overall, mark(row.connection), mark(row.discovery),
                mark(row.mapping), mark(row.conversion), mark(row.schemaSql), mark(row.dataMigration),
                mark(row.validation)].forEach(function (text) {
                var td = document.createElement('td');
                td.textContent = text;
                if (text === 'PARTIAL SUPPORT') { td.className = 'text-warning'; }
                tr.appendChild(td);
            });
            body.appendChild(tr);
        });
        var notes = document.getElementById('compat-notes');
        if (rows[0] && rows[0].limitations) {
            notes.textContent = rows[0].limitations[rows[0].limitations.length - 1];
        }
    }).catch(function (e) {
        DataSphere.notify('danger', e.message || 'Could not load the compatibility matrix.');
    });
})();
