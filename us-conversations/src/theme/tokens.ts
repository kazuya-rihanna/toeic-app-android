import React from 'react';

/* ============================================================
   Material Design 3 Design Tokens — US Conversations Theme
   Primary: Mint Green (#34D399 / #10B981)
   Dark mode first, glassmorphism対応のrgba値使用
============================================================ */

export const materialTokens = {
  light: {
    surface: '#F0FDF8',
    'surface-dim': '#C9E9D9',
    'surface-bright': '#F0FDF8',
    'surface-container-lowest': '#FFFFFF',
    'surface-container-low': '#E8F8F0',
    'surface-container': '#DCF2EA',
    'surface-container-high': '#D0EBE2',
    'surface-container-highest': '#C4E4DB',
    'surface-variant': '#CCE8DA',
    'surface-tint': '#059669',
    'on-surface': '#0F1F19',
    'on-surface-variant': '#3A4D43',
    'inverse-on-surface': '#E8F8F0',
    'inverse-surface': '#1F352C',
    primary: '#059669',
    'on-primary': '#FFFFFF',
    'primary-container': '#A7F3D0',
    'on-primary-container': '#002917',
    'inverse-primary': '#34D399',
    secondary: '#475E55',
    'on-secondary': '#FFFFFF',
    'secondary-container': '#C8E6D8',
    'on-secondary-container': '#031D12',
    tertiary: '#3B6471',
    'on-tertiary': '#FFFFFF',
    'tertiary-container': '#BEE9F8',
    'on-tertiary-container': '#001F28',
    error: '#BA1A1A',
    'on-error': '#FFFFFF',
    'error-container': '#FFDAD6',
    'on-error-container': '#410002',
    outline: '#6A7E75',
    'outline-variant': '#BBCDC3',
    scrim: '#000000',
    shadow: '#000000',
  },
  dark: {
    // Solid background for body
    surface: '#0B0F19',
    'surface-dim': '#0B0F19',
    'surface-bright': '#303840',
    // rgba values for glassmorphism cards
    'surface-container-lowest': 'rgba(7, 11, 21, 0.90)',
    'surface-container-low': 'rgba(13, 18, 30, 0.85)',
    'surface-container': 'rgba(22, 29, 48, 0.65)',
    'surface-container-high': 'rgba(30, 39, 63, 0.85)',
    'surface-container-highest': 'rgba(36, 46, 72, 0.95)',
    'surface-variant': 'rgba(42, 53, 69, 0.70)',
    'surface-tint': '#34D399',
    // Text
    'on-surface': '#F3F4F6',
    'on-surface-variant': '#9CA3AF',
    'inverse-on-surface': '#0F1F19',
    'inverse-surface': '#DEE4DF',
    // Primary (Mint Green)
    primary: '#10B981',
    'on-primary': '#003822',
    'primary-container': 'rgba(16, 185, 129, 0.15)',
    'on-primary-container': '#34D399',
    'inverse-primary': '#059669',
    // Secondary
    secondary: '#6B7280',
    'on-secondary': '#1E352A',
    'secondary-container': 'rgba(107, 114, 128, 0.20)',
    'on-secondary-container': '#9CA3AF',
    // Tertiary
    tertiary: '#60A5FA',
    'on-tertiary': '#003563',
    'tertiary-container': 'rgba(96, 165, 250, 0.15)',
    'on-tertiary-container': '#93C5FD',
    // Error
    error: '#F87171',
    'on-error': '#690005',
    'error-container': 'rgba(239, 68, 68, 0.15)',
    'on-error-container': '#FCA5A5',
    // Borders
    outline: '#4B5563',
    'outline-variant': 'rgba(255, 255, 255, 0.08)',
    scrim: '#000000',
    shadow: '#000000',
  }
};

export const typescale: Record<string, React.CSSProperties> = {
  'display-large':  { fontSize: '57px', lineHeight: '64px',  fontWeight: 400, letterSpacing: '-0.25px' },
  'display-medium': { fontSize: '45px', lineHeight: '52px',  fontWeight: 400, letterSpacing: '0px' },
  'display-small':  { fontSize: '36px', lineHeight: '44px',  fontWeight: 400, letterSpacing: '0px' },
  'headline-large': { fontSize: '32px', lineHeight: '40px',  fontWeight: 400, letterSpacing: '0px' },
  'headline-medium':{ fontSize: '28px', lineHeight: '36px',  fontWeight: 400, letterSpacing: '0px' },
  'headline-small': { fontSize: '24px', lineHeight: '32px',  fontWeight: 400, letterSpacing: '0px' },
  'title-large':    { fontSize: '22px', lineHeight: '28px',  fontWeight: 400, letterSpacing: '0px' },
  'title-medium':   { fontSize: '16px', lineHeight: '24px',  fontWeight: 500, letterSpacing: '0.15px' },
  'title-small':    { fontSize: '14px', lineHeight: '20px',  fontWeight: 500, letterSpacing: '0.1px' },
  'label-large':    { fontSize: '14px', lineHeight: '20px',  fontWeight: 500, letterSpacing: '0.1px' },
  'label-medium':   { fontSize: '12px', lineHeight: '16px',  fontWeight: 500, letterSpacing: '0.5px' },
  'label-small':    { fontSize: '11px', lineHeight: '16px',  fontWeight: 500, letterSpacing: '0.5px' },
  'body-large':     { fontSize: '16px', lineHeight: '24px',  fontWeight: 400, letterSpacing: '0.5px' },
  'body-medium':    { fontSize: '14px', lineHeight: '20px',  fontWeight: 400, letterSpacing: '0.25px' },
  'body-small':     { fontSize: '12px', lineHeight: '16px',  fontWeight: 400, letterSpacing: '0.4px' },
};

export const elevation: Record<string, React.CSSProperties> = {
  level0: { boxShadow: 'none' },
  level1: { boxShadow: '0px 1px 2px rgba(0,0,0,0.3), 0px 1px 3px 1px rgba(0,0,0,0.15)' },
  level2: { boxShadow: '0px 1px 2px rgba(0,0,0,0.3), 0px 2px 6px 2px rgba(0,0,0,0.15)' },
  level3: { boxShadow: '0px 4px 8px 3px rgba(0,0,0,0.15), 0px 1px 3px rgba(0,0,0,0.3)' },
  level4: { boxShadow: '0px 6px 10px 4px rgba(0,0,0,0.15), 0px 2px 3px rgba(0,0,0,0.3)' },
  level5: { boxShadow: '0px 8px 12px 6px rgba(0,0,0,0.15), 0px 4px 4px rgba(0,0,0,0.3)' },
};

export const motionTokens = {
  easing: {
    standard:    'cubic-bezier(0.2, 0.0, 0, 1.0)',
    decelerate:  'cubic-bezier(0.0, 0.0, 0.2, 1.0)',
    accelerate:  'cubic-bezier(0.4, 0.0, 1, 1.0)',
  },
  duration: {
    short1: '50ms',  short2: '100ms', short3: '150ms', short4: '200ms',
    medium1: '250ms', medium2: '300ms', medium3: '350ms', medium4: '400ms',
    long1: '450ms',  long2: '500ms',  long3: '550ms',  long4: '600ms',
  }
};
