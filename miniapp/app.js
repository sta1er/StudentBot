// Telegram Mini App JavaScript
class TelegramMiniApp {
    constructor() {
        this.tg = window.Telegram?.WebApp;
        this.user = null;
        this.userStats = {
            booksCount: 0,
            booksLimit: 5,
            maxFileSizeMB: 100,
            subscription: 'FREE'
        };
        this.apiBaseUrl = 'https://luvtok.ru/api/miniapp';
        this.supportedTypes = [
            'application/pdf',
            'application/vnd.openxmlformats-officedocument.wordprocessingml.document',
            'text/plain'
        ];

        this.init();
    }

    async init() {
        try {
            // Строгая проверка Telegram Web App
            if (!this.tg || !this.tg.initData || this.tg.initData.trim() === '') {
                this.showTelegramRequiredError();
                return;
            }

            // Инициализация Telegram Web App
            this.tg.ready();
            this.tg.expand();

            // Адаптация к теме
            this.adaptTheme();

            // Настройка обработчиков событий
            this.setupEventListeners();

            // Аутентификация пользователя
            await this.authenticateUser();

        } catch (error) {
            console.error('Ошибка инициализации:', error);
            this.showAuthError('Ошибка инициализации приложения');
        }
    }

    showTelegramRequiredError() {
        document.body.innerHTML = `
            <div class="error-container">
                <div class="error-icon">🚫</div>
                <h2>Доступ ограничен</h2>
                <p>Это приложение доступно только через Telegram.</p>
                <p>Пожалуйста, откройте его через официальное приложение Telegram.</p>
                <div class="mt-24">
                    <h3>Как открыть:</h3>
                    <ol style="text-align: left; max-width: 300px; margin: 0 auto;">
                        <li>Откройте Telegram</li>
                        <li>Найдите бота StudentBot</li>
                        <li>Нажмите кнопку "Открыть приложение"</li>
                    </ol>
                </div>
            </div>
        `;
    }

    adaptTheme() {
        const body = document.body;
        if (this.tg && this.tg.themeParams) {
            const themeParams = this.tg.themeParams;

            // Применяем параметры темы
            if (themeParams.bg_color) {
                document.documentElement.style.setProperty('--tg-bg-color', themeParams.bg_color);
            }
            if (themeParams.text_color) {
                document.documentElement.style.setProperty('--tg-text-color', themeParams.text_color);
            }
            if (themeParams.hint_color) {
                document.documentElement.style.setProperty('--tg-hint-color', themeParams.hint_color);
            }
            if (themeParams.button_color) {
                document.documentElement.style.setProperty('--tg-button-color', themeParams.button_color);
            }
            if (themeParams.button_text_color) {
                document.documentElement.style.setProperty('--tg-button-text-color', themeParams.button_text_color);
            }
            if (themeParams.secondary_bg_color) {
                document.documentElement.style.setProperty('--tg-secondary-bg-color', themeParams.secondary_bg_color);
            }

            // Применение класса темы
            const colorScheme = this.tg.colorScheme || 'light';
            body.classList.add(`tg-theme-${colorScheme}`);
        } else {
            body.classList.add('tg-theme-light');
        }
    }

    setupEventListeners() {
        const uploadArea = document.getElementById('upload-area');
        const fileInput = document.getElementById('file-input');
        const uploadBtn = document.getElementById('upload-btn');

        // Обработчики загрузки файлов
        if (uploadBtn) {
            uploadBtn.addEventListener('click', (e) => {
                e.preventDefault();
                e.stopPropagation();
                console.log('Upload button clicked');
                fileInput.click();
            });
        }

        if (fileInput) {
            fileInput.addEventListener('change', (e) => {
                console.log('File input changed:', e.target.files);
                if (e.target.files && e.target.files[0]) {
                    this.handleFileSelect(e.target.files[0]);
                }
            });
        }

        // Drag & Drop
        if (uploadArea) {
            uploadArea.addEventListener('dragover', (e) => this.handleDragOver(e));
            uploadArea.addEventListener('dragleave', (e) => this.handleDragLeave(e));
            uploadArea.addEventListener('drop', (e) => this.handleDrop(e));

            // Клик по области загрузки (но не по кнопке)
            uploadArea.addEventListener('click', (e) => {
                if (e.target !== uploadBtn && !uploadBtn?.contains(e.target)) {
                    fileInput.click();
                }
            });
        }

        // Кнопка подписки
        const subscriptionBtn = document.querySelector('.subscription-btn');
        if (subscriptionBtn) {
            subscriptionBtn.addEventListener('click', () => {
                this.showNotification('Свяжитесь с поддержкой для оформления подписки', 'info');
            });
        }

        // Обработчик изменения темы
        if (this.tg) {
            this.tg.onEvent('themeChanged', () => this.adaptTheme());
        }

        // Обработчик для закрытия модального окна
        const modal = document.getElementById('confirm-modal');
        if (modal) {
            const modalOverlay = modal.querySelector('.modal-overlay');
            if (modalOverlay) {
                modalOverlay.addEventListener('click', () => this.closeModal());
            }
        }
    }

    async authenticateUser() {
        try {
            console.log('Authenticating user...');
            const initData = this.tg.initData;

            if (!initData) {
                throw new Error('Отсутствуют данные инициализации Telegram');
            }

            const response = await this.apiRequest('/auth', {
                method: 'POST',
                body: JSON.stringify({ initData }),
                headers: { 'Content-Type': 'application/json' }
            });

            console.log('Auth response:', response);
            this.user = response;

            await this.loadUserData();
            this.showMainScreen();

        } catch (error) {
            console.error('Ошибка аутентификации:', error);
            this.showAuthError(error.message);
        }
    }

    async loadUserData() {
        try {
            console.log('Loading user data...');
            const response = await this.apiRequest(`/books/${this.user.telegramId}`);

            console.log('Books response:', response);

            // Используем данные с backend вместо хардкода
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
        console.log('Updating UI with user:', this.user);
        console.log('User stats:', this.userStats);

        // Обновление информации пользователя
        const userNameElement = document.getElementById('user-name');
        if (userNameElement) {
            userNameElement.textContent = this.user?.firstName || 'Пользователь';
        }

        const subscriptionBadge = document.getElementById('user-subscription');
        if (subscriptionBadge) {
            subscriptionBadge.textContent = this.userStats.subscription;
            subscriptionBadge.className = `subscription-badge ${this.userStats.subscription.toLowerCase()}`;
        }

        // Обновление статистики
        const booksCountElement = document.getElementById('books-count');
        if (booksCountElement) {
            booksCountElement.textContent = this.userStats.booksCount;
        }

        const booksLimitElement = document.getElementById('books-limit');
        if (booksLimitElement) {
            booksLimitElement.textContent = this.userStats.booksLimit;
        }

        const fileSizeLimitElement = document.getElementById('file-size-limit');
        if (fileSizeLimitElement) {
            fileSizeLimitElement.textContent = `${this.userStats.maxFileSizeMB} МБ`;
        }

        // Обновление прогресс-бара
        const progressBar = document.getElementById('books-progress');
        if (progressBar) {
            const progressPercent = (this.userStats.booksCount / this.userStats.booksLimit) * 100;
            progressBar.style.width = `${progressPercent}%`;
        }

        // Обновление аватара
        const avatar = document.getElementById('user-avatar');
        if (avatar && this.user?.firstName) {
            avatar.textContent = this.user.firstName.charAt(0).toUpperCase();
        }
    }

    renderBooksList(books) {
        const booksList = document.getElementById('books-list');
        if (!booksList) return;

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
                    <div class="book-title">${book.title || 'Без названия'}</div>
                    <div class="book-meta">
                        ${this.formatFileSize(book.fileSize || 0)} • 
                        ${this.formatDate(book.uploadDate)}
                    </div>
                </div>
                <div class="book-actions">
                    <button class="btn-icon delete" onclick="app.deleteBook(${book.id})" title="Удалить">
                        🗑️
                    </button>
                </div>
            </div>
        `).join('');
    }

    async deleteBook(bookId) {
        if (!confirm('Вы уверены, что хотите удалить эту книгу?')) {
            return;
        }

        try {
            await this.apiRequest(`/books/${bookId}?telegramId=${this.user.telegramId}`, {
                method: 'DELETE'
            });

            this.showNotification('Книга успешно удалена', 'success');
            await this.loadUserData(); // Перезагружаем список

        } catch (error) {
            console.error('Ошибка удаления книги:', error);
            this.showNotification('Ошибка при удалении книги', 'error');
        }
    }

    async handleFileSelect(file) {
        console.log('File selected:', file);

        if (!this.isValidFileType(file)) {
            this.showNotification('Неподдерживаемый тип файла. Загружайте PDF, DOCX или TXT файлы.', 'error');
            return;
        }

        if (!this.isValidFileSize(file)) {
            this.showNotification(`Файл слишком большой. Максимальный размер: ${this.userStats.maxFileSizeMB} МБ`, 'error');
            return;
        }

        if (this.userStats.booksCount >= this.userStats.booksLimit) {
            this.showNotification(`Достигнут лимит книг (${this.userStats.booksLimit}). Удалите ненужные книги или обновите план.`, 'error');
            return;
        }

        await this.uploadFile(file);
    }

    async uploadFile(file) {
        try {
            this.showUploadProgress(0);

            const formData = new FormData();
            formData.append('file', file);
            formData.append('telegramId', this.user.telegramId);

            const response = await this.apiRequest('/upload', {
                method: 'POST',
                body: formData
            });

            this.hideUploadProgress();
            this.showNotification(`Файл "${response.filename}" успешно загружен!`, 'success');

            // Перезагружаем данные
            await this.loadUserData();

        } catch (error) {
            this.hideUploadProgress();
            console.error('Ошибка загрузки файла:', error);

            let errorMessage = 'Ошибка при загрузке файла';
            if (error.status === 409) {
                errorMessage = 'Достигнут лимит количества книг';
            } else if (error.status === 413) {
                errorMessage = 'Файл слишком большой';
            } else if (error.status === 415) {
                errorMessage = 'Неподдерживаемый тип файла';
            }

            this.showNotification(errorMessage, 'error');
        }
    }

    isValidFileType(file) {
        return this.supportedTypes.includes(file.type) ||
            file.name.toLowerCase().endsWith('.pdf') ||
            file.name.toLowerCase().endsWith('.docx') ||
            file.name.toLowerCase().endsWith('.txt');
    }

    isValidFileSize(file) {
        const maxSizeBytes = this.userStats.maxFileSizeMB * 1024 * 1024;
        return file.size <= maxSizeBytes;
    }

    // API запросы с проверкой HTTP статусов
    async apiRequest(endpoint, options = {}) {
        const url = this.apiBaseUrl + endpoint;
        console.log('API Request:', url, options);

        try {
            const response = await fetch(url, options);
            console.log('API Response status:', response.status);

            if (!response.ok) {
                const error = new Error(`HTTP ${response.status}`);
                error.status = response.status;
                throw error;
            }

            // Если есть контент, парсим JSON
            const contentType = response.headers.get('content-type');
            if (contentType && contentType.includes('application/json')) {
                const data = await response.json();
                console.log('API Response data:', data);
                return data;
            }

            return null;

        } catch (error) {
            console.error('API Request failed:', error);
            throw error;
        }
    }

    // Вспомогательные методы
    formatFileSize(bytes) {
        if (bytes === 0) return '0 B';
        const k = 1024;
        const sizes = ['B', 'KB', 'MB', 'GB'];
        const i = Math.floor(Math.log(bytes) / Math.log(k));
        return parseFloat((bytes / Math.pow(k, i)).toFixed(1)) + ' ' + sizes[i];
    }

    formatDate(dateString) {
        if (!dateString) return 'Неизвестно';
        const date = new Date(dateString);
        return date.toLocaleDateString('ru-RU');
    }

    showMainScreen() {
        document.getElementById('loading-screen').style.display = 'none';
        document.getElementById('auth-error-screen').style.display = 'none';
        document.getElementById('main-screen').style.display = 'flex';
    }

    showAuthError(message) {
        console.log('❌ Showing auth error:', message);
        const loadingScreen = document.getElementById('loading-screen');
        const authErrorScreen = document.getElementById('auth-error-screen');
        const mainScreen = document.getElementById('main-screen');

        if (loadingScreen) loadingScreen.classList.remove('active');
        if (mainScreen) mainScreen.classList.remove('active');
        if (authErrorScreen) {
            authErrorScreen.classList.add('active');
            const errorMessage = authErrorScreen.querySelector('.error-message');
            if (errorMessage) {
                errorMessage.textContent = message;
            }
        }
    }

    showUploadProgress(percent) {
        const progressContainer = document.querySelector('.upload-progress');
        if (!progressContainer) {
            const uploadArea = document.getElementById('upload-area');
            if (uploadArea) {
                uploadArea.insertAdjacentHTML('afterend', `
                    <div class="upload-progress">
                        <div class="progress-info">
                            <span>Загрузка файла...</span>
                            <span class="progress-percent">0%</span>
                        </div>
                        <div class="progress-bar">
                            <div class="progress-fill" style="width: 0%"></div>
                        </div>
                    </div>
                `);
            }
        }

        const progressFill = document.querySelector('.progress-fill');
        const progressPercent = document.querySelector('.progress-percent');

        if (progressFill) progressFill.style.width = `${percent}%`;
        if (progressPercent) progressPercent.textContent = `${Math.round(percent)}%`;
    }

    hideUploadProgress() {
        const progressContainer = document.querySelector('.upload-progress');
        if (progressContainer) {
            progressContainer.remove();
        }
    }

    showNotification(message, type = 'info') {
        const notificationsContainer = document.querySelector('.notifications') || this.createNotificationsContainer();

        const notification = document.createElement('div');
        notification.className = `notification ${type}`;
        notification.textContent = message;

        notificationsContainer.appendChild(notification);

        setTimeout(() => {
            notification.remove();
        }, 5000);
    }

    createNotificationsContainer() {
        const container = document.createElement('div');
        container.className = 'notifications';
        document.body.appendChild(container);
        return container;
    }

    // Drag & Drop обработчики
    handleDragOver(e) {
        e.preventDefault();
        e.stopPropagation();
        e.currentTarget.classList.add('dragover');
    }

    handleDragLeave(e) {
        e.preventDefault();
        e.stopPropagation();
        e.currentTarget.classList.remove('dragover');
    }

    handleDrop(e) {
        e.preventDefault();
        e.stopPropagation();
        e.currentTarget.classList.remove('dragover');

        const files = e.dataTransfer.files;
        if (files.length > 0) {
            this.handleFileSelect(files[0]);
        }
    }

    delay(ms) {
        return new Promise(resolve => setTimeout(resolve, ms));
    }
}

// Инициализация приложения
const app = new TelegramMiniApp();