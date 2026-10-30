import React from 'react';
import ReactDOM from 'react-dom/client';
import { AppRouter } from './app/router/AppRouter';
import { PrototypeProvider } from './app/providers/PrototypeProvider';
import './styles/global.css';
import './styles/home.css';
import './styles/auth.css';
import './styles/profile.css';
import './styles/search.css';
import './styles/project.css';
import './styles/project-management.css';
import './styles/social.css';
import './styles/follow-matching.css';
import './styles/messaging.css';
import './styles/administration.css';
import './styles/presentation.css';
import './styles/account.css';
import './styles/project-workspace.css';
import './styles/chat-kick.css';
import { MessagingProvider } from './app/providers/MessagingProvider';
import { ProjectProvider } from './app/providers/ProjectProvider';
import { ProfileProvider } from './app/providers/ProfileProvider';
import { AuthProvider } from './app/providers/AuthProvider';
import { HomeProvider } from './app/providers/HomeProvider';
ReactDOM.createRoot(document.getElementById('root')!).render(<React.StrictMode><PrototypeProvider><AuthProvider><ProfileProvider><ProjectProvider><MessagingProvider><HomeProvider><AppRouter/></HomeProvider></MessagingProvider></ProjectProvider></ProfileProvider></AuthProvider></PrototypeProvider></React.StrictMode>);

import "./styles/matching-chat.css";

import "./styles/responsive.css";
