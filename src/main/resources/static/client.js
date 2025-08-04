document.addEventListener('DOMContentLoaded', () => {
    // --- Elementos da UI ---
    const loginView = document.getElementById('login-view');
    const chatView = document.getElementById('chat-view');
    const loginBtn = document.getElementById('login-btn');
    const usernameInput = document.getElementById('username-input');
    const radiusInput = document.getElementById('radius-input');
    const loginStatus = document.getElementById('login-status');

    const currentUserSpan = document.getElementById('current-user');
    const currentRadiusSpan = document.getElementById('current-radius');
    const contactsListDiv = document.getElementById('contacts-list');
    const messagesArea = document.getElementById('messages-area');
    const recipientLabel = document.getElementById('recipient-label');
    const messageInput = document.getElementById('message-input');
    const sendBtn = document.getElementById('send-btn');

    // --- Estado da Aplicação ---
    let stompClient = null;
    let currentUser = null;
    let selectedRecipient = null;
    let contactsInterval = null;

    // --- Lógica de Login ---
    loginBtn.addEventListener('click', () => {
        const username = usernameInput.value.trim();
        const radius = parseFloat(radiusInput.value);

        if (!username || !radius) {
            loginStatus.textContent = 'Por favor, preencha todos os campos.';
            loginStatus.className = 'text-center mt-3 text-danger';
            return;
        }

        loginStatus.textContent = 'A obter a sua localização...';
        loginStatus.className = 'text-center mt-3 text-info';

        navigator.geolocation.getCurrentPosition(
            (position) => {
                const { latitude, longitude } = position.coords;
                loginStatus.textContent = `Localização obtida! A fazer login como ${username}...`;
                performLogin(username, radius, latitude, longitude);
            },
            (error) => {
                console.error("Erro de Geolocalização:", error);
                loginStatus.textContent = 'Não foi possível obter a sua localização. Por favor, permita o acesso.';
                loginStatus.className = 'text-center mt-3 text-danger';
            }
        );
    });

    async function performLogin(username, radius, latitude, longitude) {
        try {
            const response = await fetch('/api/users/login', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ username, communicationRadiusKm: radius, latitude, longitude })
            });

            if (!response.ok) throw new Error('Falha no login com o servidor.');

            currentUser = await response.json();

            loginView.style.display = 'none';
            chatView.style.display = 'block';
            currentUserSpan.textContent = currentUser.username;
            currentRadiusSpan.textContent = currentUser.communicationRadiusKm;

            connectWebSocket();
            updateContacts();
            contactsInterval = setInterval(updateContacts, 10000); // Atualiza os contactos a cada 10 segundos

        } catch (error) {
            loginStatus.textContent = error.message;
            loginStatus.className = 'text-center mt-3 text-danger';
        }
    }

    // --- Lógica de WebSocket ---
    function connectWebSocket() {
        const socket = new SockJS('/ws-chat');
        stompClient = Stomp.over(socket);
        stompClient.connect({ login: currentUser.username },
            (frame) => {
                console.log('Conectado ao WebSocket: ' + frame);
                // Subscreve à fila pessoal para receber mensagens diretas (SYNC) e offline (ASYNC)
                stompClient.subscribe(`/user/${currentUser.username}/queue/messages`, (message) => {
                    const msg = JSON.parse(message.body);
                    displayMessage(msg, false);
                });
            },
            (error) => { console.error('Erro de WebSocket:', error); }
        );
    }

    // --- Lógica de Contactos ---
    async function updateContacts() {
        if (!currentUser) return;
        try {
            const response = await fetch(`/api/users/${currentUser.username}/contacts`);
            const contacts = await response.json();
            contactsListDiv.innerHTML = '';

            contacts.forEach(contact => {
                const contactDiv = document.createElement('a');
                contactDiv.href = '#';
                contactDiv.className = 'list-group-item list-group-item-action d-flex justify-content-between align-items-center';
                contactDiv.onclick = () => selectRecipient(contact);

                const statusIndicator = contact.status === 'ONLINE' ? '🟢' : '🔴';
                const commTypeClass = contact.communicationType === 'SYNC' ? 'bg-success' : 'bg-warning text-dark';

                contactDiv.innerHTML = `
                    <div>
                        ${statusIndicator} ${contact.username}
                        <small class="d-block text-muted">${contact.distanceKm.toFixed(2)} km</small>
                    </div>
                    <span class="badge ${commTypeClass}">${contact.communicationType}</span>
                `;
                contactsListDiv.appendChild(contactDiv);
            });
        } catch (error) {
            console.error('Erro ao atualizar contactos:', error);
        }
    }

    function selectRecipient(contact) {
        selectedRecipient = contact;
        recipientLabel.textContent = `${contact.username} (${contact.communicationType})`;
        // Remove a classe 'active' de todos os itens e adiciona ao selecionado
        document.querySelectorAll('#contacts-list a').forEach(a => a.classList.remove('active'));
        event.currentTarget.classList.add('active');
    }

    // --- Lógica de Envio de Mensagens ---
    sendBtn.addEventListener('click', async () => {
        const content = messageInput.value.trim();
        if (!content || !selectedRecipient) {
            alert('Por favor, selecione um contacto e digite uma mensagem.');
            return;
        }

        const message = {
            sender: currentUser.username,
            recipient: selectedRecipient.username,
            content: content,
        };

        try {
            await fetch('/api/messages/send', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify(message)
            });
            displayMessage(message, true); // Mostra a mensagem enviada na tela
            messageInput.value = '';
        } catch (error) {
            console.error('Erro ao enviar mensagem:', error);
            alert('Falha ao enviar mensagem.');
        }
    });

    // --- Lógica de Exibição ---
    function displayMessage(msg, isSent) {
        const messageDiv = document.createElement('div');
        messageDiv.className = `message ${isSent ? 'sent' : 'received'}`;

        const senderName = isSent ? 'Você' : msg.sender;
        messageDiv.innerHTML = `<div class="sender">${senderName}</div><div>${msg.content}</div>`;

        messagesArea.appendChild(messageDiv);
        messagesArea.scrollTop = messagesArea.scrollHeight;
    }
});
