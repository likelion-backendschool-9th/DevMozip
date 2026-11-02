'use client';
import styles from './Input.module.css';
import type { InputHTMLAttributes, TextareaHTMLAttributes } from 'react';
export function Input({className='',...props}:InputHTMLAttributes<HTMLInputElement>){return <input className={`${styles.control} form-input ${className}`} {...props}/>;}
export function Textarea({className='',...props}:TextareaHTMLAttributes<HTMLTextAreaElement>){return <textarea className={`${styles.control} form-input ${className}`} {...props}/>;}
