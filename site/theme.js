(() => {
    let theme = 'dark';
    try {
        theme = localStorage.getItem('theme') === 'light' ? 'light' : 'dark';
    } catch {}
    document.documentElement.dataset.theme = theme;
    document.querySelector('meta[name="theme-color"]')?.setAttribute('content', theme === 'light' ? '#f3f0e9' : '#1b1916');
})();
