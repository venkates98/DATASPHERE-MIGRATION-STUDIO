/* Sign-in page behaviour: show/hide password, inline validation, caps-lock hint, loading state. */
(function () {
    'use strict';

    var form = document.getElementById('login-form');
    if (!form) {
        return;
    }
    var username = document.getElementById('username');
    var password = document.getElementById('password');
    var toggle = document.getElementById('toggle-password');
    var submit = document.getElementById('login-submit');
    var spinner = document.getElementById('login-spinner');
    var label = document.getElementById('login-label');
    var capsHint = document.getElementById('caps-hint');

    function setInvalid(input, invalid) {
        input.classList.toggle('is-invalid', invalid);
        input.setAttribute('aria-invalid', invalid ? 'true' : 'false');
    }

    function validate(input) {
        var empty = input.value.trim().length === 0;
        // Passwords may legitimately start/end with spaces, so only reject a completely empty value.
        if (input === password) {
            empty = input.value.length === 0;
        }
        setInvalid(input, empty);
        return !empty;
    }

    [username, password].forEach(function (input) {
        input.addEventListener('blur', function () { if (input.value !== '' || input.classList.contains('is-invalid')) { validate(input); } });
        input.addEventListener('input', function () { if (input.classList.contains('is-invalid')) { validate(input); } });
    });

    toggle.addEventListener('click', function () {
        var show = password.type === 'password';
        password.type = show ? 'text' : 'password';
        toggle.setAttribute('aria-pressed', show ? 'true' : 'false');
        toggle.setAttribute('aria-label', show ? 'Hide password' : 'Show password');
        toggle.querySelector('i').className = show ? 'bi bi-eye-slash' : 'bi bi-eye';
        password.focus();
    });

    password.addEventListener('keyup', function (e) {
        if (e.getModifierState) {
            capsHint.hidden = !e.getModifierState('CapsLock');
        }
    });
    password.addEventListener('blur', function () { capsHint.hidden = true; });

    form.addEventListener('submit', function (e) {
        var okUser = validate(username);
        var okPass = validate(password);
        if (!okUser || !okPass) {
            e.preventDefault();
            (okUser ? password : username).focus();
            return;
        }
        submit.disabled = true;
        spinner.classList.remove('d-none');
        label.textContent = 'Signing in\u2026';
    });

    // The browser back/forward cache can restore the page with the loading state still active.
    window.addEventListener('pageshow', function (e) {
        if (e.persisted) {
            submit.disabled = false;
            spinner.classList.add('d-none');
            label.textContent = 'Sign in';
        }
    });
})();
