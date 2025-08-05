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
    const discoverListDiv = document.getElementById('discover-list');
    const messagesArea = document.getElementById('messages-area');
    const recipientLabel = document.getElementById('recipient-label');
    const messageInput = document.getElementById('message-input');
    const sendBtn = document.getElementById('send-btn');

    const newRadiusInput = document.getElementById('new-radius-input');
    const updateRadiusBtn = document.getElementById('update-radius-btn');
    const updateLocationBtn = document.getElementById('update-location-btn');
    const updateStatus = document.getElementById('update-status');

    // --- Estado da Aplicação ---
    let stompClient = null;
    let currentUser = null;
    let selectedRecipient = null;
    // let contactsInterval = null; // REMOVIDO

    // --- Lógica de Login e Conexão ---
    loginBtn.addEventListener('click', () => {
        const username = usernameInput.value.trim();
        const radius = parseFloat(radiusInput.value);
        if (!username || !radius) {
            loginStatus.textContent = 'Por favor, preencha todos os campos.';
            return;
        }
        loginBtn.disabled = true;
        loginStatus.textContent = 'A obter a sua localização...';
        navigator.geolocation.getCurrentPosition(
            (position) => {
                let { latitude, longitude } = position.coords;
                latitude += (Math.random() - 0.5) * 0.001;
                longitude += (Math.random() - 0.5) * 0.001;
                performLogin(username, radius, latitude, longitude);
            },
            (error) => {
                loginStatus.textContent = 'Não foi possível obter a sua localização.';
                loginBtn.disabled = false;
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
            refreshAllLists();
            // contactsInterval = setInterval(refreshAllLists, 10000); // REMOVIDO
        } catch (error) {
            loginStatus.textContent = error.message;
            loginBtn.disabled = false;
        }
    }

    function connectWebSocket() {
        const socket = new SockJS('/ws-chat');
        stompClient = Stomp.over(socket);
        stompClient.connect({ login: currentUser.username },
            (frame) => {
                // Subscreve à fila de mensagens
                stompClient.subscribe(`/user/${currentUser.username}/queue/messages`, (message) => {
                    const msg = JSON.parse(message.body);
                    displayMessage(msg, false);
                });

                // Subscreve à nova fila de notificações
                stompClient.subscribe(`/user/${currentUser.username}/queue/notifications`, (notification) => {
                    console.log("Notificação de atualização recebida!");
                    refreshAllLists();
                });

                fetchOfflineMessages();
            },
            (error) => { console.error('Erro de WebSocket:', error); }
        );
    }

    async function fetchOfflineMessages() {
        const response = await fetch(`/api/users/${currentUser.username}/offline-messages`);
        const messages = await response.json();
        if (messages.length > 0) {
            displaySystemMessage(`A carregar ${messages.length} mensagens offline...`);
            messages.forEach(msg => { displayMessage(msg, false); });
        }
    }

    async function refreshAllLists() {
        await updateContacts();
        await updateDiscoverList();
    }

    async function updateContacts() {
        if (!currentUser) return;
        const response = await fetch(`/api/users/${currentUser.username}/contacts`);
        const contacts = await response.json();
        contactsListDiv.innerHTML = '';
        contacts.forEach(contact => {
            const contactDiv = document.createElement('a');
            contactDiv.href = '#';
            contactDiv.className = 'list-group-item list-group-item-action';
            contactDiv.onclick = () => selectRecipient(contact);
            const statusIndicator = contact.status === 'ONLINE' ? '🟢' : '🔴';
            const commTypeClass = contact.communicationType === 'SYNC' ? 'bg-success' : 'bg-warning text-dark';
            contactDiv.innerHTML = `
                <div class="d-flex justify-content-between align-items-center">
                    <div>
                        ${statusIndicator} ${contact.username}
                        <small class="d-block text-muted">${contact.distanceKm.toFixed(2)} km</small>
                    </div>
                    <span class="badge ${commTypeClass}">${contact.communicationType}</span>
                </div>
                <button class="btn btn-sm btn-outline-danger mt-1" onclick="removeContact(event, '${contact.username}')">Remover</button>
            `;
            contactsListDiv.appendChild(contactDiv);
        });
    }

    async function updateDiscoverList() {
        if (!currentUser) return;
        const response = await fetch(`/api/users/${currentUser.username}/discover`);
        const users = await response.json();
        discoverListDiv.innerHTML = '';
        users.forEach(user => {
            const userDiv = document.createElement('div');
            userDiv.className = 'list-group-item d-flex justify-content-between align-items-center';
            const statusIndicator = user.status === 'ONLINE' ? '🟢' : '🔴';
            userDiv.innerHTML = `
                <span>${statusIndicator} ${user.username}</span>
                <button class="btn btn-sm btn-outline-primary" onclick="addContact('${user.username}')">Adicionar</button>
            `;
            discoverListDiv.appendChild(userDiv);
        });
    }

    window.addContact = async (contactUsername) => {
        await fetch(`/api/users/${currentUser.username}/contacts`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ username: contactUsername })
        });
        await refreshAllLists();
    };

    window.removeContact = async (event, contactUsername) => {
        event.stopPropagation();
        await fetch(`/api/users/${currentUser.username}/contacts/${contactUsername}`, { method: 'DELETE' });
        if (selectedRecipient && selectedRecipient.username === contactUsername) {
            selectedRecipient = null;
            recipientLabel.textContent = 'Ninguém';
        }
        await refreshAllLists();
    };

    function selectRecipient(contact) {
        selectedRecipient = contact;
        recipientLabel.textContent = `${contact.username} (${contact.communicationType})`;
        document.querySelectorAll('#contacts-list a').forEach(a => a.classList.remove('active'));
        event.currentTarget.classList.add('active');
    }

    updateRadiusBtn.addEventListener('click', async () => {
        const newRadius = parseFloat(newRadiusInput.value);
        if (!newRadius || newRadius <= 0) {
            updateStatus.textContent = 'Raio inválido.';
            return;
        }
        updateStatus.textContent = 'A atualizar...';
        await fetch(`/api/users/${currentUser.username}/radius`, {
            method: 'PUT',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ radius: newRadius })
        });
        currentUser.communicationRadiusKm = newRadius;
        currentRadiusSpan.textContent = newRadius;
        newRadiusInput.value = '';
        updateStatus.textContent = 'Raio atualizado!';
        // await refreshAllLists(); // Removido - a notificação tratará disto
    });
    updateLocationBtn.addEventListener('click', () => {
        updateStatus.textContent = 'A obter nova localização...';
        navigator.geolocation.getCurrentPosition(
            async (position) => {
                const { latitude, longitude } = position.coords;
                await fetch(`/api/users/${currentUser.username}/location`, {
                    method: 'PUT',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({ latitude, longitude })
                });
                currentUser.latitude = latitude;
                currentUser.longitude = longitude;
                updateStatus.textContent = 'Localização atualizada!';
                // await refreshAllLists(); // Removido - a notificação tratará disto
            },
            (error) => {
                updateStatus.textContent = 'Falha ao obter localização.';
            }
        );
    });
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
        await fetch('/api/messages/send', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(message)
        });
        displayMessage(message, true);
        messageInput.value = '';
    });
    function displayMessage(msg, isSent) {
        const messageDiv = document.createElement('div');
        messageDiv.className = `message ${isSent ? 'sent' : 'received'}`;
        const senderName = isSent ? 'Você' : msg.sender;
        messageDiv.innerHTML = `<div class="sender">${senderName}</div><div>${msg.content}</div>`;
        messagesArea.appendChild(messageDiv);
        messagesArea.scrollTop = messagesArea.scrollHeight;
    }
    function displaySystemMessage(text) {
        const messageDiv = document.createElement('div');
        messageDiv.className = 'text-center text-muted fst-italic my-2';
        messageDiv.textContent = text;
        messagesArea.appendChild(messageDiv);
        messagesArea.scrollTop = messagesArea.scrollHeight;
    }
});
