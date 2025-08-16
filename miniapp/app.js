// Telegram Mini App JavaScript
class TelegramMiniApp {
    constructor() {
        this.tg = window.Telegram?.WebApp;
        this.isMockMode = !this.tg || !this.tg.initData;
        this.user = null;
        this.userStats = {
            booksCount: 0,
            booksLimit: 5,
            maxFileSizeMB: 100,
            subscription: 'FREE'
        };
        this.apiBaseUrl = 'https://luvtok.ru/api/miniapp';
        this.supportedTypes = ['application/pdf', 'application/vnd.openxmlformats-officedocument.wordprocessingml.document', 'text/plain'];
        this.maxFileSizes = { FREE: 100, PREMIUM: 500, BUSINESS: 1000 };
        this.maxBooks = { FREE: 5, PREMIUM: 25, BUSINESS: 100 };
        
        // Инициализация mock API если нужно
        if (this.isMockMode) {
            this.mockAPI = new MockAPI();
        }
        
        this.init();
    }

    async init() {
        try {
            // Инициализация Telegram Web App только если доступен
            if (this.tg) {
                this.tg.ready();
                this.tg.expand();
            }
            
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

    adaptTheme() {
        const body = document.body;
        
        if (this.tg && this.tg.themeParams) {
            const themeParams = this.tg.themeParams;
            
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
            // Применение светлой темы по умолчанию для mock режима
            body.classList.add('tg-theme-light');
        }
    }

    setupEventListeners() {
        const uploadArea = document.getElementById('upload-area');
        const fileInput = document.getElementById('file-input');
        const uploadBtn = document.getElementById('upload-btn');

        // Обработчики загрузки файлов
        uploadBtn.addEventListener('click', (e) => {
            e.preventDefault();
            e.stopPropagation();
            console.log('Upload button clicked');
            fileInput.click();
        });

        fileInput.addEventListener('change', (e) => {
            console.log('File input changed:', e.target.files);
            if (e.target.files && e.target.files[0]) {
                this.handleFileSelect(e.target.files[0]);
            }
        });

        // Drag & Drop
        uploadArea.addEventListener('dragover', (e) => this.handleDragOver(e));
        uploadArea.addEventListener('dragleave', (e) => this.handleDragLeave(e));
        uploadArea.addEventListener('drop', (e) => this.handleDrop(e));
        
        // Клик по области загрузки (но не по кнопке)
        uploadArea.addEventListener('click', (e) => {
            if (e.target !== uploadBtn && !uploadBtn.contains(e.target)) {
                fileInput.click();
            }
        });

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

        // Обработчик для закрытия модального окна по клику на overlay
        const modal = document.getElementById('confirm-modal');
        const modalOverlay = modal.querySelector('.modal-overlay');
        modalOverlay.addEventListener('click', () => this.closeModal());
    }

    async authenticateUser() {
        try {
            let response;
            
            if (this.isMockMode) {
                // Использование mock аутентификации для демонстрации
                await this.delay(1500); // Симуляция загрузки
                response = await this.mockAPI.auth('mock_init_data');
            } else {
                const initData = this.tg.initData;
                if (!initData) {
                    throw new Error('Отсутствуют данные инициализации Telegram');
                }

                response = await this.apiRequest('/auth', {
                    method: 'POST',
                    body: JSON.stringify({ initData }),
                    headers: { 'Content-Type': 'application/json' }
                });
            }

            if (!response.success) {
                throw new Error(response.message || 'Ошибка аутентификации');
            }

            this.user = response.user;
            await this.loadUserData();
            this.showMainScreen();

        } catch (error) {
            console.error('Ошибка аутентификации:', error);
            this.showAuthError(error.message);
        }
    }

    async loadUserData() {
        try {
            let response;
            
            if (this.isMockMode) {
                response = await this.mockAPI.getBooks(this.user.telegramId);
            } else {
                response = await this.apiRequest(`/books/${this.user.telegramId}`);
            }
            
            if (response.success) {
                this.userStats = {
                    booksCount: response.books?.length || 0,
                    booksLimit: this.maxBooks[this.user.subscription] || 5,
                    maxFileSizeMB: this.maxFileSizes[this.user.subscription] || 100,
                    subscription: this.user.subscription || 'FREE'
                };
                this.updateUI();
                this.renderBooksList(response.books || []);
            }
        } catch (error) {
            console.error('Ошибка загрузки данных:', error);
            this.showNotification('Ошибка загрузки данных пользователя', 'error');
        }
    }

    updateUI() {
        // Обновление информации пользователя
        document.getElementById('user-name').textContent = this.user.firstName || 'Пользователь';
        const subscriptionBadge = document.getElementById('user-subscription');
        subscriptionBadge.textContent = this.userStats.subscription;
        subscriptionBadge.className = `subscription-badge ${this.userStats.subscription.toLowerCase()}`;

        // Обновление статистики
        document.getElementById('books-count').textContent = this.userStats.booksCount;
        document.getElementById('books-limit').textContent = this.userStats.booksLimit;
        document.getElementById('file-size-limit').textContent = `${this.userStats.maxFileSizeMB} МБ`;

        // Обновление прогресс-бара
        const progressPercent = (this.userStats.booksCount / this.userStats.booksLimit) * 100;
        document.getElementById('books-progress').style.width = `${progressPercent}%`;

        // Обновление аватара
        const avatar = document.getElementById('user-avatar');
        if (this.user.firstName) {
            avatar.textContent = this.user.firstName.charAt(0).toUpperCase();
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

        uploadProgress.classList.remove('hidden');
        uploadFileName.textContent = file.name;

        try {
            let result;
            
            if (this.isMockMode) {
                const formData = new FormData();
                formData.append('file', file);
                formData.append('telegramId', this.user.telegramId);
                
                // Симуляция прогресса загрузки
                await this.simulateProgress(uploadPercentage, uploadProgressFill);
                result = await this.mockAPI.uploadFile(formData);
            } else {
                const formData = new FormData();
                formData.append('file', file);
                formData.append('telegramId', this.user.telegramId);

                const response = await fetch(`${this.apiBaseUrl}/upload`, {
                    method: 'POST',
                    body: formData
                });

                result = await response.json();

                if (!response.ok || !result.success) {
                    throw new Error(result.message || 'Ошибка загрузки файла');
                }
            }

            this.showNotification('Файл успешно загружен!', 'success');
            uploadProgress.classList.add('hidden');
            
            // Сброс input для возможности повторного выбора того же файла
            document.getElementById('file-input').value = '';
            
            // Обновление данных
            await this.loadUserData();

        } catch (error) {
            console.error('Ошибка загрузки:', error);
            this.showNotification(error.message || 'Ошибка загрузки файла', 'error');
            uploadProgress.classList.add('hidden');
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
        this.showModal(
            'Удалить книгу?',
            `Вы уверены, что хотите удалить "${bookTitle}"?`,
            () => this.deleteBook(bookId)
        );
    }

    async deleteBook(bookId) {
        try {
            let response;
            
            if (this.isMockMode) {
                response = await this.mockAPI.deleteBook(bookId);
            } else {
                response = await this.apiRequest(`/books/${bookId}`, {
                    method: 'DELETE'
                });
            }

            if (!response.success) {
                throw new Error(response.message || 'Ошибка удаления');
            }

            this.showNotification('Книга удалена', 'success');
            await this.loadUserData();

        } catch (error) {
            console.error('Ошибка удаления:', error);
            this.showNotification(error.message || 'Ошибка удаления книги', 'error');
        }
    }

    showModal(title, message, onConfirm) {
        const modal = document.getElementById('confirm-modal');
        const titleEl = document.getElementById('confirm-title');
        const messageEl = document.getElementById('confirm-message');
        const confirmBtn = document.getElementById('confirm-action');

        console.log('Showing modal:', title, message);

        titleEl.textContent = title;
        messageEl.textContent = message;
        
        // Удаляем предыдущий обработчик
        confirmBtn.onclick = null;
        
        confirmBtn.onclick = () => {
            console.log('Confirm button clicked');
            onConfirm();
            this.closeModal();
        };

        modal.classList.remove('hidden');
        
        // Vibration feedback
        if (this.tg && this.tg.HapticFeedback) {
            this.tg.HapticFeedback.impactOccurred('medium');
        }
    }

    closeModal() {
        console.log('Closing modal');
        document.getElementById('confirm-modal').classList.add('hidden');
    }

    showNotification(message, type = 'info') {
        const notifications = document.getElementById('notifications');
        const notification = document.createElement('div');
        notification.className = `notification ${type}`;
        notification.textContent = message;

        notifications.appendChild(notification);

        // Vibration feedback
        if (this.tg && this.tg.HapticFeedback) {
            const intensity = type === 'error' ? 'heavy' : 'light';
            this.tg.HapticFeedback.impactOccurred(intensity);
        }

        setTimeout(() => {
            notification.remove();
        }, 5000);
    }

    showMainScreen() {
        document.getElementById('loading-screen').classList.remove('active');
        document.getElementById('main-screen').classList.add('active');
    }

    showAuthError(message) {
        document.getElementById('loading-screen').classList.remove('active');
        document.getElementById('auth-error-message').textContent = message;
        document.getElementById('auth-error-screen').classList.add('active');
    }

    async apiRequest(endpoint, options = {}) {
        const url = `${this.apiBaseUrl}${endpoint}`;
        const defaultOptions = {
            headers: {
                'Content-Type': 'application/json',
                'Authorization': `Bearer ${this.tg.initData}`
            }
        };

        const response = await fetch(url, { ...defaultOptions, ...options });
        
        if (!response.ok) {
            throw new Error(`HTTP error! status: ${response.status}`);
        }

        return await response.json();
    }

    delay(ms) {
        return new Promise(resolve => setTimeout(resolve, ms));
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

// Mock API для демонстрации
class MockAPI {
    constructor() {
        this.books = [
            {
                id: 1,
                fileName: 'Война и мир.pdf',
                title: 'Война и мир',
                fileSize: 5242880, // 5MB
                uploadDate: new Date(Date.now() - 86400000).toISOString() // вчера
            },
            {
                id: 2,
                fileName: 'JavaScript Guide.pdf',
                title: 'JavaScript Guide',
                fileSize: 2097152, // 2MB
                uploadDate: new Date(Date.now() - 172800000).toISOString() // 2 дня назад
            }
        ];
        this.bookIdCounter = 3;
    }

    async auth(initData) {
        await this.delay(1000);
        return {
            success: true,
            user: {
                telegramId: 12345,
                firstName: 'Иван',
                subscription: 'FREE'
            }
        };
    }

    async getBooks(telegramId) {
        await this.delay(500);
        return {
            success: true,
            books: [...this.books] // Возвращаем копию массива
        };
    }

    async uploadFile(formData) {
        await this.delay(2000);
        const file = formData.get('file');
        const book = {
            id: this.bookIdCounter++,
            fileName: file.name,
            title: file.name.replace(/\.[^/.]+$/, ""),
            fileSize: file.size,
            uploadDate: new Date().toISOString()
        };
        this.books.push(book);
        return { success: true, book };
    }

    async deleteBook(bookId) {
        await this.delay(500);
        const bookIndex = this.books.findIndex(book => book.id === parseInt(bookId));
        if (bookIndex > -1) {
            this.books.splice(bookIndex, 1);
        }
        return { success: true };
    }

    delay(ms) {
        return new Promise(resolve => setTimeout(resolve, ms));
    }
}

// Глобальные функции для обработчиков событий
function closeModal() {
    if (window.miniApp) {
        window.miniApp.closeModal();
    }
}

// Инициализация приложения после загрузки DOM
document.addEventListener('DOMContentLoaded', () => {
    window.miniApp = new TelegramMiniApp();
});