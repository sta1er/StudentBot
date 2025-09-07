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
        const uploadAgainBtn = document.getElementById('upload-again-btn');

        // Открытие диалога выбора файла по клику на кнопки
        uploadBtn?.addEventListener('click', () => fileInput.click());
        uploadAgainBtn?.addEventListener('click', () => fileInput.click());

        fileInput?.addEventListener('change', (e) => {
            if (e.target.files?.[0]) this.handleFileSelect(e.target.files[0]);
        });

        // Drag & Drop
        if (uploadArea) {
            uploadArea.addEventListener('dragover', (e) => this.handleDragOver(e));
            uploadArea.addEventListener('dragleave', (e) => this.handleDragLeave(e));
            uploadArea.addEventListener('drop', (e) => this.handleDrop(e));
            uploadArea.addEventListener('click', () => fileInput.click());
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
        document.getElementById('finish-btn')?.addEventListener('click', () => this.tg.close());

        document.getElementById('app-modal')?.addEventListener('click', (e) => {
            if (e.target.classList.contains('modal-overlay') || e.target.id === 'modal-cancel') {
                this.closeModal();
            }
        });
    }

    resetUploadUI() {
        document.getElementById('initial-upload-container')?.classList.remove('hidden');
        document.getElementById('post-upload-container')?.classList.add('hidden');
    }

    showPostUploadUI() {
        document.getElementById('initial-upload-container')?.classList.add('hidden');
        document.getElementById('post-upload-container')?.classList.remove('hidden');
    }

    async authenticateUser() {
        try {
            const response = await this.apiRequest('/auth', {
                method: 'POST',
                body: JSON.stringify({ initData: this.tg.initData }),
                headers: { 'Content-Type': 'application/json' }
            });

            // НОВАЯ ЛОГИКА: Проверяем ответ на подписку
            if (response.error === 'SUBSCRIPTION_REQUIRED') {
                this.showSubscriptionRequiredModal(response);
                return;
            }

            this.user = response;
            await this.loadUserData();
            this.showScreen('main-screen');
        } catch (error) {
            console.error('Ошибка аутентификации:', error);

            // Проверяем, не ошибка ли подписки в catch блоке
            if (error.response && error.response.error === 'SUBSCRIPTION_REQUIRED') {
                this.showSubscriptionRequiredModal(error.response);
                return;
            }

            this.showAuthError(error.message);
        }
    }

    /**
     * НОВЫЙ МЕТОД: Показ модального окна о необходимости подписки
     * Использует СУЩЕСТВУЮЩУЮ модальную систему - НЕ МЕНЯЕТ ДИЗАЙН!
     */
    showSubscriptionRequiredModal(subscriptionData) {
        console.log('Показываем модальное окно о подписке:', subscriptionData);

        const channelName = subscriptionData.channelName || '@chota_study';
        const channelUrl = subscriptionData.channelUrl || 'https://t.me/chota_study';
        const userName = subscriptionData.userName || 'Пользователь';

        this.showModal({
            title: '🔒 Требуется подписка',
            message: `Привет, ${userName}! 👋\n\nДля использования бота на бесплатном тарифе необходимо подписаться на канал:\n\n📢 ${channelName}\n\nПосле подписки нажмите "Проверить подписку" для продолжения.`,
            confirmText: '📢 Подписаться',
            cancelText: '✅ Проверить подписку',
            showCancel: true,
            onConfirm: () => {
                // Открываем канал в Telegram
                this.openChannel(channelUrl);
            },
            onCancel: () => {
                // Проверяем подписку
                this.checkSubscription();
            }
        });
    }

    /**
     * НОВЫЙ МЕТОД: Открытие канала
     */
    openChannel(channelUrl) {
        console.log('Открываем канал:', channelUrl);
        if (this.tg && this.tg.openTelegramLink) {
            this.tg.openTelegramLink(channelUrl);
        } else {
            window.open(channelUrl, '_blank');
        }
    }

    /**
     * НОВЫЙ МЕТОД: Проверка подписки
     */
    async checkSubscription() {
        try {
            console.log('Проверяем подписку...');

            const response = await this.apiRequest('/check-subscription', {
                method: 'POST',
                body: JSON.stringify({ telegramId: this.user?.telegramId || 0 }),
                headers: { 'Content-Type': 'application/json' }
            });

            if (response.subscribed) {
                // Подписка подтверждена - перезагружаем приложение
                this.showNotification('✅ Подписка подтверждена! Добро пожаловать!', 'success');
                // Повторно аутентифицируемся для получения доступа
                setTimeout(() => {
                    window.location.reload();
                }, 1500);
            } else {
                // Подписка не найдена
                this.showNotification('❌ ' + response.message, 'error');

                // Показываем модальное окно снова через 2 секунды
                setTimeout(() => {
                    this.showSubscriptionRequiredModal({
                        channelName: response.channelName,
                        channelUrl: response.channelUrl,
                        userName: this.user?.firstName || 'Пользователь'
                    });
                }, 2000);
            }
        } catch (error) {
            console.error('Ошибка проверки подписки:', error);
            this.showNotification('Ошибка проверки подписки. Попробуйте еще раз.', 'error');
        }
    }

    async loadUserData() {
        try {
            const response = await this.apiRequest(`/books/${this.user.telegramId}`);

            // Проверяем на ошибку подписки и в загрузке данных
            if (response.error === 'SUBSCRIPTION_REQUIRED') {
                this.showSubscriptionRequiredModal(response);
                return;
            }

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

            // Проверяем на ошибку подписки и в catch блоке
            if (error.response && error.response.error === 'SUBSCRIPTION_REQUIRED') {
                this.showSubscriptionRequiredModal(error.response);
                return;
            }

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
            booksList.innerHTML = `
                <div class="empty-state">
                    <div class="empty-icon">📚</div>
                    <p>У вас пока нет загруженных книг</p>
                </div>
            `;
            return;
        }

        booksList.innerHTML = books.map(book => `
            <div class="book-item" data-book-id="${book.id}">
                <div class="book-info">
                    <div class="book-title">${this.escapeHtml(book.title || book.fileName)}</div>
                    <div class="book-meta">
                        ${this.formatFileSize(book.fileSize)} • ${this.formatDate(book.uploadDate)}
                    </div>
                </div>
                <div class="book-actions">
                    <button class="btn-icon delete" onclick="window.miniApp.confirmDelete(${book.id}, '${this.escapeHtml(book.title || book.fileName)}')">
                        🗑️
                    </button>
                </div>
            </div>
        `).join('');
    }

    handleDragOver(e) {
        e.preventDefault();
        e.stopPropagation();
        document.getElementById('upload-area').classList.add('dragover');
    }

    handleDragLeave(e) {
        e.preventDefault();
        e.stopPropagation();
        document.getElementById('upload-area').classList.remove('dragover');
    }

    handleDrop(e) {
        e.preventDefault();
        e.stopPropagation();
        document.getElementById('upload-area').classList.remove('dragover');

        const files = e.dataTransfer.files;
        if (files.length > 0) {
            this.handleFileSelect(files[0]);
        }
    }

    async handleFileSelect(file) {
        if (!file) return;

        console.log('Handling file:', file.name, file.type, file.size);

        // Проверка лимита книг
        if (this.userStats.booksCount >= this.userStats.booksLimit) {
            this.showNotification(`Достигнут лимит в ${this.userStats.booksLimit} книг. Оформите подписку для увеличения лимита.`, 'error');
            return;
        }

        // Проверка типа файла
        if (!this.supportedTypes.includes(file.type)) {
            this.showNotification('Поддерживаемые форматы: PDF, DOCX, TXT', 'error');
            return;
        }

        // Проверка размера файла
        const fileSizeMB = file.size / (1024 * 1024);
        if (fileSizeMB > this.userStats.maxFileSizeMB) {
            this.showNotification(`Размер файла превышает ${this.userStats.maxFileSizeMB} МБ`, 'error');
            return;
        }

        await this.uploadFile(file);
    }

    async uploadFile(file) {
        const uploadProgress = document.getElementById('upload-progress');
        const uploadFileName = document.getElementById('upload-file-name');
        const uploadPercentage = document.getElementById('upload-percentage');
        const uploadProgressFill = document.getElementById('upload-progress-fill');

        uploadProgress?.classList.remove('hidden');
        if (uploadFileName) uploadFileName.textContent = file.name;

        try {
            const formData = new FormData();
            formData.append('file', file);
            formData.append('telegramId', this.user.telegramId);

            // Симуляция прогресса загрузки
            if (uploadPercentage && uploadProgressFill) {
                await this.simulateProgress(uploadPercentage, uploadProgressFill);
            }

            const response = await fetch(`${this.apiBaseUrl}/upload`, {
                method: 'POST',
                body: formData
            });

            const result = await response.json();

            // Проверяем на ошибку подписки
            if (response.status === 403 && result.error === 'SUBSCRIPTION_REQUIRED') {
                this.showSubscriptionRequiredModal(result);
                uploadProgress?.classList.add('hidden');
                return;
            }

            if (!response.ok) {
                throw new Error(result.message || 'Ошибка загрузки файла');
            }

            this.showNotification('Файл успешно загружен!', 'success');
            uploadProgress?.classList.add('hidden');

            // Сброс input для возможности повторного выбора того же файла
            const fileInput = document.getElementById('file-input');
            if (fileInput) fileInput.value = '';

            // Обновление данных
            await this.loadUserData();

        } catch (error) {
            console.error('Ошибка загрузки:', error);
            this.showNotification(error.message || 'Ошибка загрузки файла', 'error');
            uploadProgress?.classList.add('hidden');
        }
    }

    async simulateProgress(percentageEl, progressFillEl) {
        return new Promise(resolve => {
            let progress = 0;
            const interval = setInterval(() => {
                progress += Math.random() * 15;
                if (progress > 100) progress = 100;

                percentageEl.textContent = `${Math.round(progress)}%`;
                progressFillEl.style.width = `${progress}%`;

                if (progress >= 100) {
                    clearInterval(interval);
                    resolve();
                }
            }, 100);
        });
    }

    confirmDelete(bookId, bookTitle) {
        console.log('Confirming delete for book:', bookId, bookTitle);
        this.showModal({
            title: 'Удалить книгу?',
            message: `Вы уверены, что хотите удалить "${bookTitle}"?`,
            confirmText: 'Удалить',
            cancelText: 'Отмена',
            showCancel: true,
            onConfirm: () => this.deleteBook(bookId)
        });
    }

    async deleteBook(bookId) {
        try {
            const response = await this.apiRequest(`/books/${bookId}`, {
                method: 'DELETE'
            });

            // Проверяем на ошибку подписки
            if (response.error === 'SUBSCRIPTION_REQUIRED') {
                this.showSubscriptionRequiredModal(response);
                return;
            }

            this.showNotification('Книга удалена', 'success');
            await this.loadUserData();
        } catch (error) {
            console.error('Ошибка удаления:', error);

            // Проверяем на ошибку подписки в catch блоке
            if (error.response && error.response.error === 'SUBSCRIPTION_REQUIRED') {
                this.showSubscriptionRequiredModal(error.response);
                return;
            }

            this.showNotification(error.message || 'Ошибка удаления книги', 'error');
        }
    }

    // ИСПОЛЬЗУЕМ СУЩЕСТВУЮЩУЮ МОДАЛЬНУЮ СИСТЕМУ - НЕ МЕНЯЕМ ДИЗАЙН!
    showModal({ title, message, confirmText = 'OK', cancelText = 'Отмена', showCancel = false, onConfirm, onCancel }) {
        const modal = document.getElementById('app-modal');
        const titleEl = document.getElementById('modal-title');
        const messageEl = document.getElementById('modal-message');
        const confirmBtn = document.getElementById('modal-confirm');
        const cancelBtn = document.getElementById('modal-cancel');

        if (!modal || !titleEl || !messageEl || !confirmBtn) {
            console.error('Modal elements not found');
            return;
        }

        titleEl.textContent = title;
        messageEl.textContent = message;
        confirmBtn.textContent = confirmText;

        if (cancelBtn) {
            cancelBtn.textContent = cancelText;
            cancelBtn.style.display = showCancel ? 'inline-flex' : 'none';
        }

        // Очистка предыдущих обработчиков
        const newConfirmBtn = confirmBtn.cloneNode(true);
        const newCancelBtn = cancelBtn ? cancelBtn.cloneNode(true) : null;

        confirmBtn.parentNode.replaceChild(newConfirmBtn, confirmBtn);
        if (cancelBtn && newCancelBtn) {
            cancelBtn.parentNode.replaceChild(newCancelBtn, cancelBtn);
        }

        // Новые обработчики
        newConfirmBtn.onclick = () => {
            if (onConfirm) onConfirm();
            this.closeModal();
        };

        if (newCancelBtn && onCancel) {
            newCancelBtn.onclick = () => {
                if (onCancel) onCancel();
                this.closeModal();
            };
        }

        modal.classList.remove('hidden');
    }

    closeModal() {
        const modal = document.getElementById('app-modal');
        if (modal) {
            modal.classList.add('hidden');
        }
    }

    showNotification(message, type = 'info') {
        const notifications = document.querySelector('.notifications') || this.createNotificationsContainer();
        const notification = document.createElement('div');
        notification.className = `notification ${type}`;
        notification.textContent = message;

        notifications.appendChild(notification);

        setTimeout(() => {
            notification.remove();
        }, 5000);
    }

    createNotificationsContainer() {
        const notifications = document.createElement('div');
        notifications.className = 'notifications';
        document.body.appendChild(notifications);
        return notifications;
    }

    showScreen(screenId) {
        const screens = document.querySelectorAll('.screen');
        screens.forEach(screen => screen.classList.remove('active'));

        const targetScreen = document.getElementById(screenId);
        if (targetScreen) {
            targetScreen.classList.add('active');
        }
    }

    showAuthError(message) {
        console.error('Auth error:', message);
        this.showScreen('error-screen');

        const errorMessage = document.getElementById('error-message');
        if (errorMessage) {
            errorMessage.textContent = message;
        }
    }

    async apiRequest(endpoint, options = {}) {
        const url = `${this.apiBaseUrl}${endpoint}`;

        try {
            const response = await fetch(url, options);

            if (!response.ok) {
                const errorData = await response.json().catch(() => ({}));
                const error = new Error(errorData.message || `HTTP error! status: ${response.status}`);
                error.response = errorData;
                throw error;
            }

            return await response.json();
        } catch (error) {
            if (error.response) {
                throw error; // Пробрасываем ошибку с response данными
            }
            throw new Error(error.message || 'Network error');
        }
    }

    formatFileSize(bytes) {
        if (!bytes) return '0 Б';
        const k = 1024;
        const sizes = ['Б', 'КБ', 'МБ', 'ГБ'];
        const i = Math.floor(Math.log(bytes) / Math.log(k));
        return parseFloat((bytes / Math.pow(k, i)).toFixed(2)) + ' ' + sizes[i];
    }

    formatDate(dateString) {
        const date = new Date(dateString);
        return date.toLocaleDateString('ru-RU', {
            day: '2-digit',
            month: '2-digit',
            year: 'numeric'
        });
    }

    escapeHtml(text) {
        const div = document.createElement('div');
        div.textContent = text;
        return div.innerHTML;
    }
}

// Инициализация приложения после загрузки DOM
document.addEventListener('DOMContentLoaded', () => {
    window.miniApp = new TelegramMiniApp();
});