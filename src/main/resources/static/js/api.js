/**
 * TicketRush Shared API Client, Auth Helpers & Toast System
 */
const API_BASE = '/api';

const api = {
  getToken() {
    return localStorage.getItem('tr_token');
  },

  getUser() {
    const raw = localStorage.getItem('tr_user');
    return raw ? JSON.parse(raw) : null;
  },

  setAuth(token, user) {
    localStorage.setItem('tr_token', token);
    localStorage.setItem('tr_user', JSON.stringify(user));
  },

  clearAuth() {
    localStorage.removeItem('tr_token');
    localStorage.removeItem('tr_user');
  },

  toast(message, type = 'info') {
    let container = document.getElementById('toast-container');
    if (!container) {
      container = document.createElement('div');
      container.id = 'toast-container';
      container.className = 'toast-container';
      document.body.appendChild(container);
    }

    const toast = document.createElement('div');
    toast.className = `toast toast-${type}`;

    const icon = type === 'success' ? '✅' : type === 'error' ? '❌' : 'ℹ️';
    toast.textContent = `${icon} ${message}`;

    container.appendChild(toast);

    setTimeout(() => {
      toast.style.opacity = '0';
      toast.style.transform = 'translateY(-10px)';
      toast.style.transition = 'all 0.3s ease';
      setTimeout(() => toast.remove(), 300);
    }, 3500);
  },

  /**
   * Safely parses UTC ISO timestamps returned by the Spring Boot backend
   * ensuring correct conversion regardless of client browser timezone (e.g. IST).
   */
  parseUtc(dateStr) {
    if (!dateStr) return 0;
    if (typeof dateStr === 'number') return dateStr;
    const str = String(dateStr).trim();
    if (str.endsWith('Z') || /[+-]\d{2}:?\d{2}$/.test(str)) {
      return new Date(str).getTime();
    }
    return new Date(str + 'Z').getTime();
  },

  formatDateTime(dateStr) {
    const ms = this.parseUtc(dateStr);
    if (!ms) return '';
    const d = new Date(ms);
    return d.toLocaleDateString(undefined, { weekday: 'short', month: 'short', day: 'numeric', year: 'numeric' }) + ' at ' + d.toLocaleTimeString(undefined, { hour: '2-digit', minute: '2-digit' });
  },

  async request(endpoint, options = {}) {
    const headers = options.headers || {};
    headers['Content-Type'] = 'application/json';

    const token = this.getToken();
    if (token) {
      headers['Authorization'] = `Bearer ${token}`;
    }

    const config = {
      ...options,
      headers
    };

    try {
      const response = await fetch(endpoint.startsWith('/api') ? endpoint : `${API_BASE}${endpoint}`, config);

      if (response.status === 401) {
        this.clearAuth();
        if (!window.location.pathname.endsWith('login.html') && !window.location.pathname.endsWith('register.html')) {
          this.toast('Session expired. Please sign in again.', 'error');
          setTimeout(() => { window.location.href = '/login.html'; }, 1000);
        }
        throw new Error('Authentication required');
      }

      const isJson = response.headers.get('content-type')?.includes('application/json');
      const data = isJson ? await response.json() : null;

      if (!response.ok) {
        const errorMsg = data?.message || data?.error || `HTTP error ${response.status}`;
        throw new Error(errorMsg);
      }

      return data;
    } catch (err) {
      console.error(`API Error [${endpoint}]:`, err);
      throw err;
    }
  },

  get(endpoint) {
    return this.request(endpoint, { method: 'GET' });
  },

  post(endpoint, body) {
    return this.request(endpoint, {
      method: 'POST',
      body: JSON.stringify(body)
    });
  },

  put(endpoint, body) {
    return this.request(endpoint, {
      method: 'PUT',
      body: JSON.stringify(body)
    });
  },

  patch(endpoint, body) {
    return this.request(endpoint, {
      method: 'PATCH',
      body: JSON.stringify(body)
    });
  },

  renderNav() {
    this.renderNavbar();
  },

  renderNavbar() {
    const navRight = document.getElementById('nav-right');
    if (!navRight) return;

    const isAdminPage = window.location.pathname.endsWith('admin.html') || window.location.pathname.endsWith('dashboard.html');

    // Render Secondary Sub-Nav Bar on consumer pages (NOT on admin console)
    const header = document.querySelector('header');
    if (header && !isAdminPage && !document.querySelector('.sub-nav')) {
      const subNav = document.createElement('div');
      subNav.className = 'sub-nav';
      subNav.innerHTML = `
        <div class="sub-nav-container">
          <div class="sub-nav-left">
            <a href="/index.html">Movies</a>
            <a href="/index.html?category=Concerts" class="active">Events <span class="sub-nav-tag">LIVE</span></a>
            <a href="/index.html?category=Theater">Plays</a>
            <a href="/index.html?category=Sports">Sports</a>
            <a href="/index.html?category=Comedy">Standup Comedy</a>
            <a href="/index.html">Activities</a>
          </div>
          <div class="sub-nav-right">
            <a href="/organizer.html">Host Event</a>
            <a href="/index.html">Offers</a>
            <a href="/index.html">Gift Cards</a>
          </div>
        </div>
      `;
      header.insertAdjacentElement('afterend', subNav);
    }

    const user = this.getUser();
    navRight.textContent = ''; // clear

    // Add City Selector only for consumer views
    if (!isAdminPage) {
      const citySelector = document.createElement('div');
      citySelector.className = 'city-selector';
      const currentCity = localStorage.getItem('tr_city') || 'Mumbai';
      citySelector.innerHTML = `📍 <span>${currentCity}</span> ▾`;
      citySelector.title = "Click to change city";
      citySelector.onclick = () => {
        const cities = ['Mumbai', 'Delhi-NCR', 'Bengaluru', 'Hyderabad', 'Pune', 'Chandigarh', 'San Francisco', 'New York'];
        const idx = (cities.indexOf(currentCity) + 1) % cities.length;
        const nextCity = cities[idx];
        localStorage.setItem('tr_city', nextCity);
        citySelector.querySelector('span').textContent = nextCity;
        api.toast(`Switched city to ${nextCity}`, 'info');
        const cityInput = document.getElementById('search-city');
        if (cityInput) {
          cityInput.value = nextCity === 'Mumbai' ? '' : nextCity;
          if (typeof window.loadEvents === 'function') window.loadEvents();
        }
      };
      navRight.appendChild(citySelector);
    }

    if (user) {
      const userBadge = document.createElement('div');
      userBadge.className = 'user-badge';
      userBadge.style.cursor = 'pointer';
      userBadge.style.position = 'relative';
      userBadge.title = 'Account Menu (Tap to Open)';

      const userName = document.createElement('span');
      userName.textContent = user.name;
      userName.style.fontWeight = '600';

      const rolePill = document.createElement('span');
      rolePill.className = `role-pill role-${user.role}`;
      rolePill.textContent = user.role;

      userBadge.appendChild(userName);
      userBadge.appendChild(rolePill);

      // Sleek User Menu Dropdown (Essential for Mobile & One-Tap Access)
      const dropdown = document.createElement('div');
      dropdown.className = 'user-dropdown-menu';
      dropdown.style.cssText = 'display: none; position: absolute; top: calc(100% + 10px); right: 0; min-width: 220px; background: #161b29; border: 1.5px solid rgba(255,255,255,0.15); border-radius: 14px; box-shadow: 0 16px 36px rgba(0,0,0,0.85); z-index: 9999; padding: 0.5rem; flex-direction: column; gap: 0.25rem;';

      dropdown.innerHTML = `
        <div style="padding: 0.6rem 0.85rem; border-bottom: 1px solid rgba(255,255,255,0.08); margin-bottom: 0.25rem;">
          <div style="font-weight: 800; color: #fff; font-size: 0.88rem;">${user.name}</div>
          <div style="font-size: 0.72rem; color: #94a3b8; overflow: hidden; text-overflow: ellipsis;">${user.email}</div>
        </div>
        <a href="/profile.html" class="dropdown-item">⚙️ Account Settings</a>
        ${user.role === 'CUSTOMER' ? '<a href="/my-bookings.html" class="dropdown-item">🎟️ My Bookings</a>' : ''}
        ${user.role === 'ORGANIZER' ? '<a href="/organizer.html" class="dropdown-item">🎪 Organizer Studio</a>' : ''}
        ${user.role === 'ADMIN' ? '<a href="/dashboard.html" class="dropdown-item">⚡ Admin Console</a>' : ''}
        <a href="/index.html" class="dropdown-item">🌐 Browse Events</a>
        <div style="border-top: 1px solid rgba(255,255,255,0.08); margin-top: 0.25rem; padding-top: 0.25rem;">
          <button type="button" class="dropdown-item dropdown-logout-btn" style="width: 100%; text-align: left; background: none; border: none; cursor: pointer; color: #f87171; font-weight: 700;">🚪 Sign Out</button>
        </div>
      `;

      dropdown.querySelector('.dropdown-logout-btn').onclick = (e) => {
        e.stopPropagation();
        api.clearAuth();
        api.toast('Signed out successfully.', 'info');
        setTimeout(() => { window.location.href = '/index.html'; }, 400);
      };

      userBadge.onclick = (e) => {
        e.stopPropagation();
        const isOpen = dropdown.style.display === 'flex';
        document.querySelectorAll('.user-dropdown-menu').forEach(d => { d.style.display = 'none'; });
        dropdown.style.display = isOpen ? 'none' : 'flex';
      };

      userBadge.appendChild(dropdown);
      navRight.appendChild(userBadge);

      document.addEventListener('click', (e) => {
        if (!userBadge.contains(e.target)) {
          dropdown.style.display = 'none';
        }
      });

      if (isAdminPage) {
        // In the Admin Console: provide Storefront switch
        const storefrontBtn = document.createElement('a');
        storefrontBtn.href = '/index.html';
        storefrontBtn.className = 'btn btn-secondary btn-sm nav-desktop-only';
        storefrontBtn.style.padding = '0.45rem 0.9rem';
        storefrontBtn.style.fontWeight = '700';
        storefrontBtn.innerHTML = '🌐 View Storefront';
        navRight.appendChild(storefrontBtn);
      } else {
        // Show role-specific primary actions on desktop
        if (user.role === 'CUSTOMER') {
          const myBookingsLink = document.createElement('a');
          myBookingsLink.href = '/my-bookings.html';
          myBookingsLink.className = 'nav-desktop-only';
          myBookingsLink.textContent = 'My Bookings';
          myBookingsLink.style.fontWeight = '700';
          navRight.appendChild(myBookingsLink);

          const profileLink = document.createElement('a');
          profileLink.href = '/profile.html';
          profileLink.className = 'nav-desktop-only';
          profileLink.textContent = 'Account';
          profileLink.style.fontWeight = '700';
          navRight.appendChild(profileLink);
        }

        if (user.role === 'ORGANIZER') {
          const orgLink = document.createElement('a');
          orgLink.href = '/organizer.html';
          orgLink.className = 'nav-desktop-only';
          orgLink.textContent = 'Organizer Studio';
          orgLink.style.fontWeight = '700';
          navRight.appendChild(orgLink);
        }

        if (user.role === 'ADMIN') {
          const adminLink = document.createElement('a');
          adminLink.href = '/dashboard.html';
          adminLink.className = 'nav-desktop-only';
          adminLink.textContent = 'Admin Console';
          adminLink.style.fontWeight = '700';
          navRight.appendChild(adminLink);
        }
      }

      const logoutBtn = document.createElement('button');
      logoutBtn.className = 'btn btn-secondary btn-sm nav-desktop-only';
      logoutBtn.textContent = 'Sign Out';
      logoutBtn.onclick = () => {
        api.clearAuth();
        api.toast('Signed out successfully.', 'info');
        setTimeout(() => { window.location.href = '/index.html'; }, 500);
      };
      navRight.appendChild(logoutBtn);
    } else {
      const loginLink = document.createElement('a');
      loginLink.href = '/login.html';
      loginLink.className = 'btn btn-primary btn-sm';
      loginLink.style.padding = '0.5rem 1.25rem';
      loginLink.textContent = 'Sign In';

      navRight.appendChild(loginLink);
    }
  }
};

window.Api = api;
window.Auth = {
  getToken: () => api.getToken(),
  getUser: () => api.getUser(),
  setAuth: (t, u) => api.setAuth(t, u),
  clearAuth: () => api.clearAuth()
};
window.showToast = (msg, type) => api.toast(msg, type);

document.addEventListener('DOMContentLoaded', () => {
  api.renderNavbar();
});
