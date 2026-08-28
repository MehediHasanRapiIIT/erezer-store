<#import "template.ftl" as layout>
<@layout.registrationLayout displayMessage=!messagesPerField.existsError('username','password') displayInfo=realm.password && realm.registrationAllowed && !registrationDisabled??; section>
    <#if section = "header">
    <#elseif section = "form">

    <div class="ez-page">

        <!-- LEFT: Login card -->
        <div class="ez-left">
            <div class="ez-left-fx" aria-hidden="true"></div>
            <div class="ez-card">

                <!-- Header -->
                <div class="ez-header">
                    <h1 class="ez-brand">EREZER</h1>
                    <p class="ez-title">Admin</p>
                    <p class="ez-sub">Sign in to your workspace</p>
                </div>
                <div class="ez-divider"></div>

                <!-- Error -->
                <#if messagesPerField.existsError('username','password')>
                    <div class="ez-error">
                        ${kcSanitize(messagesPerField.getFirstError('username','password'))?no_esc}
                    </div>
                </#if>

                <!-- Form -->
                <form id="kc-form-login" action="${url.loginAction}" method="post">

                    <div class="ez-field">
                        <label for="username" class="ez-label">
                            <#if !realm.loginWithEmailAllowed>Username
                            <#elseif !realm.registrationEmailAsUsername>Username or Email
                            <#else>Email</#if>
                        </label>
                        <input
                            id="username"
                            name="username"
                            type="text"
                            class="ez-input"
                            placeholder="admin@erezer.com"
                            value="${login.username!''}"
                            autofocus
                            autocomplete="username"
                        />
                    </div>

                    <div class="ez-field">
                        <label for="password" class="ez-label">Password</label>
                        <div class="ez-input-wrap">
                            <input
                                id="password"
                                name="password"
                                type="password"
                                class="ez-input"
                                placeholder="Enter your password"
                                autocomplete="current-password"
                            />
                            <button type="button" class="ez-eye" onclick="togglePwd()" aria-label="Toggle password">
                                <svg id="eye-svg" xmlns="http://www.w3.org/2000/svg" width="17" height="17" viewBox="0 0 24 24" fill="none" stroke="#a8a29e" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                                    <path d="M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8z"/>
                                    <circle cx="12" cy="12" r="3"/>
                                </svg>
                            </button>
                        </div>
                    </div>

                    <div class="ez-options">
                        <#if realm.rememberMe && !usernameEditDisabled??>
                            <label class="ez-remember">
                                <input type="checkbox" name="rememberMe" class="ez-check" <#if login.rememberMe??>checked</#if> />
                                <span>Keep me signed in</span>
                            </label>
                        <#else>
                            <span></span>
                        </#if>
                        <#if realm.resetPasswordAllowed>
                            <a href="${url.loginResetCredentialsUrl}" class="ez-forgot">Forgot password?</a>
                        </#if>
                    </div>

                    <input type="hidden" name="credentialId" <#if auth.selectedCredential?has_content>value="${auth.selectedCredential}"</#if>/>

                    <button type="submit" id="kc-login" class="ez-btn">
                        <span>Sign in</span>
                        <span class="ez-btn-arrow">→</span>
                    </button>

                </form>

                <div class="ez-foot"></div>
            </div>
        </div>

        <!-- RIGHT: Branding panel -->
        <div class="ez-right">
            <!-- Animated background layers -->
            <div class="ez-fx" aria-hidden="true">
                <div class="ez-fx-grid"></div>
                <div class="ez-orb ez-orb--a"></div>
                <div class="ez-orb ez-orb--b"></div>
                <div class="ez-marquee">
                    <span>EREZER&nbsp;·&nbsp;EREZER&nbsp;·&nbsp;EREZER&nbsp;·&nbsp;EREZER&nbsp;·&nbsp;</span>
                    <span>EREZER&nbsp;·&nbsp;EREZER&nbsp;·&nbsp;EREZER&nbsp;·&nbsp;EREZER&nbsp;·&nbsp;</span>
                </div>
            </div>

            <div class="ez-right-inner">
                <h2 class="ez-right-mark">EREZER</h2>
                <div class="ez-right-rule"></div>
                <p class="ez-right-subtitle">Admin Workspace</p>
                <p class="ez-right-desc">Manage your catalog, orders and customers — everything that keeps the store running, in one place.</p>
                <ul class="ez-right-list">
                    <li>Catalog, variants &amp; images</li>
                    <li>Orders &amp; fulfilment</li>
                    <li>Customers &amp; returns</li>
                    <li>Sales reports &amp; analytics</li>
                </ul>
            </div>
        </div>

    </div>

    <script>
        function togglePwd() {
            var p = document.getElementById('password');
            var s = document.getElementById('eye-svg');
            if (p.type === 'password') {
                p.type = 'text';
                s.setAttribute('stroke', '#0c0a09');
            } else {
                p.type = 'password';
                s.setAttribute('stroke', '#a8a29e');
            }
        }
    </script>

    </#if>
</@layout.registrationLayout>
