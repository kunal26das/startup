(() => {
    const themeButton = document.querySelector('.theme-toggle');
    const updateThemeButton = () => {
        const isLight = document.documentElement.dataset.theme === 'light';
        themeButton.textContent = isLight ? 'Dark' : 'Light';
        themeButton.setAttribute('aria-label', `Switch to ${isLight ? 'dark' : 'light'} theme`);
    };

    if (themeButton) {
        updateThemeButton();
        themeButton.hidden = false;
        themeButton.addEventListener('click', () => {
            const theme = document.documentElement.dataset.theme === 'light' ? 'dark' : 'light';
            document.documentElement.dataset.theme = theme;
            document.querySelector('meta[name="theme-color"]')?.setAttribute('content', theme === 'light' ? '#f3f0e9' : '#1b1916');
            try {
                localStorage.setItem('theme', theme);
            } catch {}
            updateThemeButton();
        });
    }

    const mobileMenu = document.querySelector('.mobile-menu');
    if (mobileMenu) {
        mobileMenu.querySelectorAll('a').forEach(link => {
            link.addEventListener('click', () => { mobileMenu.open = false; });
        });
        document.addEventListener('click', event => {
            if (!mobileMenu.contains(event.target)) mobileMenu.open = false;
        });
        document.addEventListener('keydown', event => {
            if (event.key === 'Escape' && mobileMenu.open) {
                mobileMenu.open = false;
                mobileMenu.querySelector('summary').focus();
            }
        });
    }

    const copyStatus = document.querySelector('.copy-status');
    document.querySelectorAll('[data-copy]').forEach(button => {
        const code = document.getElementById(button.dataset.copy);
        if (!code) return;
        button.hidden = false;
        button.addEventListener('click', async () => {
            try {
                await navigator.clipboard.writeText(code.textContent);
                button.textContent = 'Copied';
                copyStatus.textContent = button.dataset.copy === 'install-code' ? 'Gradle dependency copied.' : 'Startup manifest copied.';
            } catch {
                const selection = window.getSelection();
                const range = document.createRange();
                range.selectNodeContents(code);
                selection.removeAllRanges();
                selection.addRange(range);
                copyStatus.textContent = 'Clipboard unavailable. Code selected; use your keyboard or browser menu to copy.';
            }
            window.setTimeout(() => { button.textContent = 'Copy'; }, 2500);
        });
    });

    const nextButton = document.getElementById('demo-next');
    if (nextButton) {
        const nodes = Array.from(document.querySelectorAll('.graph-node'));
        const status = document.getElementById('demo-status');
        const names = ['Logger', 'Network', 'Analytics'];
        const messages = [
            'Step through a startup sequence.',
            'Logger is ready. Network can start.',
            'Network is ready. Analytics can start.',
            'All three are ready, dependencies first.'
        ];
        let completed = 0;
        const render = () => {
            nodes.forEach((node, index) => {
                node.classList.toggle('is-ready', index < completed);
                node.classList.toggle('is-next', index === completed);
                node.querySelector('.node-state').textContent = index < completed ? 'Ready' : index === completed ? 'Next' : 'Waiting';
            });
            nextButton.textContent = completed < names.length ? `Start ${names[completed]} →` : 'Replay ↺';
            status.textContent = messages[completed];
        };
        nextButton.addEventListener('click', () => {
            completed = completed === names.length ? 0 : completed + 1;
            render();
        });
        render();
        document.querySelector('.demo-controls').hidden = false;
    }
})();
