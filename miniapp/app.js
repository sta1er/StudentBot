class TelegramMiniApp {
    constructor() {
        this.tg = window.Telegram?.WebApp;
        this.user = null;
        this.apiBaseUrl = 'https://luvtok.ru/api/miniapp';
        this.supportedTypes = ['application/pdf', 'application/vnd.openxmlformats-officedocument.wordprocessingml.document', 'text/plain'];
        this.currentTheme = localStorage.getItem('app-theme') || 'light';

        this.init();
    }

    async init() {
        if (!this.tg || !this.tg.initData || this.tg.initData.trim() === '') {
            window.location.href = 'error.html';
            return;
        }
        try {
            this.tg.ready();
            this.tg.expand();
            this.applyTheme(this.currentTheme);
            this.setupEventListeners();
            await this.authenticateUser();
        } catch (error) {
            console.error('Ошибка инициализации:', error);
            this.showAuthError('Ошибка инициализации приложения');
        }
    }

    applyTheme(theme) {
        document.body.classList.remove('tg-theme-light', 'tg-theme-dark');
        document.body.classList.add(`tg-theme-${theme}`);
        localStorage.setItem('app-theme', theme);
        this.currentTheme = theme;
    }

    toggleTheme() {
        const newTheme = this.currentTheme === 'light' ? 'dark' : 'light';
        this.applyTheme(newTheme);
    }

    setupEventListeners() {
        const uploadArea = document.getElementById('upload-area');
        const fileInput = document.getElementById('file-input');
        const uploadBtn = document.getElementById('upload-btn');
        if (uploadBtn) uploadBtn.addEventListener('click', (e) => { e.preventDefault(); e.stopPropagation(); fileInput.click(); });
        if (fileInput) fileInput.addEventListener('change', (e) => { if (e.target.files?.[0]) this.handleFileSelect(e.target.files[0]); });
        if (uploadArea) {
            uploadArea.addEventListener('dragover', (e) => this.handleDragOver(e));
            uploadArea.addEventListener('dragleave', (e) => this.handleDragLeave(e));
            uploadArea.addEventListener('drop', (e) => this.handleDrop(e));
            uploadArea.addEventListener('click', (e) => { if (e.target !== uploadBtn && !uploadBtn?.contains(e.target)) fileInput.click(); });
        }

        document.querySelector('.subscription-btn')?.addEventListener('click', () => {
            this.showModal({
                title: '💎 Улучшение тарифа',
                message: 'Для оформления подписки и расширения возможностей, пожалуйста, свяжитесь с нашей службой поддержки в Telegram.',
                confirmText: 'Понятно',
                showCancel: false
            });
        });

        document.getElementById('theme-toggle')?.addEventListener('click', () => this.toggleTheme());

        // Новая логика для кнопки "Готово"
        document.getElementById('finish-btn')?.addEventListener('click', () => {
            this.tg.close();
        });

        document.getElementById('app-modal')?.addEventListener('click', (e) => {
            if (e.target.classList.contains('modal-overlay') || e.target.id === 'modal-cancel') {
                this.closeModal();
            }
        });
    }

    async authenticateUser() {
        try {
            const response = await this.apiRequest('/auth', {
                method: 'POST',
                body: JSON.stringify({ initData: this.tg.initData }),
                headers: { 'Content-Type': 'application/json' }
            });
            this.user = response;
            await this.loadUserData();
            this.showScreen('main-screen');
        } catch (error) {
            console.error('Ошибка аутентификации:', error);
            this.showAuthError(error.message);
        }
    }

    async loadUserData() {
        try {
            const response = await this.apiRequest(`/books/${this.user.telegramId}`);
            this.userStats = {
                booksCount: response.books?.length || 0,
                booksLimit: this.user.maxBooks,
                maxFileSizeMB: this.user.maxFileSizeMB,
                subscription: this.user.subscriptionTier
            };
            this.updateUI();
            this.renderBooksList(response.books || []);
        } catch (error) {
            console.error('Ошибка загрузки данных:', error);
            this.showNotification('Ошибка загрузки данных пользователя', 'error');
        }
    }

    updateUI() {
        document.getElementById('user-name').textContent = this.user?.firstName || 'Пользователь';
        const subBadge = document.getElementById('user-subscription');
        subBadge.textContent = this.userStats.subscription;
        subBadge.className = `subscription-badge ${this.userStats.subscription.toLowerCase()}`;
        document.getElementById('books-count').textContent = this.userStats.booksCount;
        document.getElementById('books-limit').textContent = this.userStats.booksLimit;
        document.getElementById('file-size-limit').textContent = `${this.userStats.maxFileSizeMB} МБ`;
        const progressPercent = (this.userStats.booksCount / this.userStats.booksLimit) * 100;
        document.getElementById('books-progress').style.width = `${progressPercent}%`;
        if (this.user?.firstName) {
            document.getElementById('user-avatar').textContent = this.user.firstName.charAt(0).toUpperCase();
        }
    }

    renderBooksList(books) {
        const booksList = document.getElementById('books-list');
        if (!books.length) {
            booksList.innerHTML = `<div class="empty-state"><div class="empty-icon">📚</div><p>У вас пока нет загруженных книг</p></div>`;
            return;
        }
        booksList.innerHTML = books.map(book => `
            <div class="book-item" data-book-id="${book.id}">
                <div class="book-info">
                    <div class="book-title">${book.title || 'Без названия'}</div>
                    <div class="book-meta">
                        ${this.formatFileSize(book.fileSize || 0)} • ${this.formatDate(book.uploadDate)}
                    </div>
                </div>
                <div class="book-actions">
                    <button class="btn-icon delete" title="Удалить">🗑️</button>
                </div>
            </div>`).join('');
        booksList.querySelectorAll('.delete').forEach(btn => {
            const bookId = btn.closest('.book-item').dataset.bookId;
            btn.addEventListener('click', () => this.confirmDeleteBook(bookId));
        });
    }

    confirmDeleteBook(bookId) {
        this.showModal({
            title: 'Подтвердите удаление',
            message: 'Вы уверены, что хотите удалить эту книгу? Это действие необратимо.',
            confirmText: 'Удалить',
            onConfirm: () => this.deleteBook(bookId)
        });
    }

    async deleteBook(bookId) {
        try {
            await this.apiRequest(`/books/${bookId}?telegramId=${this.user.telegramId}`, { method: 'DELETE' });
            this.showNotification('Книга успешно удалена', 'success');
            await this.loadUserData();
        } catch (error) {
            console.error('Ошибка удаления книги:', error);
            this.showNotification('Ошибка при удалении книги', 'error');
        }
    }

    async handleFileSelect(file) {
        // Прячем кнопку "Готово" при начале новой загрузки
        document.getElementById('finish-btn')?.classList.add('hidden');
        if (!this.isValidFileType(file)) { this.showNotification('Неподдерживаемый тип файла.', 'error'); return; }
        if (!this.isValidFileSize(file)) { this.showNotification(`Файл слишком большой: макс. ${this.userStats.maxFileSizeMB} МБ`, 'error'); return; }
        if (this.userStats.booksCount >= this.userStats.booksLimit) { this.showNotification('Достигнут лимит книг.', 'error'); return; }
        await this.uploadFile(file);
    }

    async uploadFile(file) {
        try {
            this.showNotification(`Загрузка файла: ${file.name}`, 'info');
            const formData = new FormData();
            formData.append('file', file);
            formData.append('telegramId', this.user.telegramId);
            const response = await this.apiRequest('/upload', { method: 'POST', body: formData });
            this.showNotification(`Файл "${response.filename}" успешно загружен!`, 'success');
            // Показываем кнопку "Готово" после успешной загрузки
            document.getElementById('finish-btn')?.classList.remove('hidden');
            await this.loadUserData();
        } catch (error) {
            let msg = 'Ошибка при загрузке файла';
            if (error.status === 409) msg = 'Достигнут лимит книг';
            else if (error.status === 413) msg = 'Файл слишком большой';
            this.showNotification(msg, 'error');
        }
    }

    isValidFileType = (file) => this.supportedTypes.includes(file.type) || ['.pdf', '.docx', '.txt'].some(ext => file.name.toLowerCase().endsWith(ext));
    isValidFileSize = (file) => file.size <= (this.userStats.maxFileSizeMB * 1024 * 1024);

    async apiRequest(endpoint, options = {}) {
        const url = this.apiBaseUrl + endpoint;
        try {
            const response = await fetch(url, options);
            if (!response.ok) {
                const error = new Error(`HTTP ${response.status}`);
                error.status = response.status;
                throw error;
            }
            if (response.headers.get('content-type')?.includes('application/json')) {
                return await response.json();
            }
            return null;
        } catch (error) {
            console.error('API Request failed:', error);
            throw error;
        }
    }

    formatFileSize(bytes) { if (bytes === 0) return '0 B'; const k = 1024; const sizes = ['B', 'KB', 'MB', 'GB']; const i = Math.floor(Math.log(bytes) / Math.log(k)); return parseFloat((bytes / Math.pow(k, i)).toFixed(1)) + ' ' + sizes[i]; }
    formatDate(dateString) { if (!dateString) return ''; try { const date = new Date(dateString); if (isNaN(date.getTime())) { return ''; } return date.toLocaleDateString('ru-RU', { day: '2-digit', month: '2-digit', year: 'numeric' }); } catch (e) { return ''; } }

    showScreen(screenId) { document.querySelectorAll('.screen').forEach(s => s.classList.remove('active')); document.getElementById(screenId)?.classList.add('active'); }
    showAuthError(message) { this.showScreen('auth-error-screen'); const errorMessage = document.querySelector('#auth-error-screen .error-message'); if (errorMessage) errorMessage.textContent = message; }

    showModal({ title, message, confirmText = 'ОК', cancelText = 'Отмена', onConfirm, showCancel = true }) {
        const modal = document.getElementById('app-modal');
        modal.querySelector('#modal-title').textContent = title;
        modal.querySelector('#modal-message').innerHTML = message;
        const confirmBtn = modal.querySelector('#modal-confirm');
        confirmBtn.textContent = confirmText;
        const cancelBtn = modal.querySelector('#modal-cancel');
        cancelBtn.style.display = showCancel ? 'inline-flex' : 'none';
        cancelBtn.textContent = cancelText;
        confirmBtn.replaceWith(confirmBtn.cloneNode(true));
        modal.querySelector('#modal-confirm').addEventListener('click', () => { if (onConfirm) onConfirm(); this.closeModal(); });
        modal.classList.remove('hidden');
    }
    closeModal() { document.getElementById('app-modal')?.classList.add('hidden'); }

    showNotification(message, type = 'info') { const container = document.querySelector('.notifications') || (() => { const el = document.createElement('div'); el.className = 'notifications'; document.body.appendChild(el); return el; })(); const notification = document.createElement('div'); notification.className = `notification ${type}`; notification.textContent = message; container.appendChild(notification); setTimeout(() => notification.remove(), 5000); }
    handleDragOver(e) { e.preventDefault(); e.stopPropagation(); e.currentTarget.classList.add('dragover'); }
    handleDragLeave(e) { e.preventDefault(); e.stopPropagation(); e.currentTarget.classList.remove('dragover'); }
    handleDrop(e) { e.preventDefault(); e.stopPropagation(); e.currentTarget.classList.remove('dragover'); if (e.dataTransfer.files?.length > 0) this.handleFileSelect(e.dataTransfer.files[0]); }
}
const app = new TelegramMiniApp();