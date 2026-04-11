import { useCallback, useEffect, useId, useRef, useState } from 'react';
import { Check, ChevronDown } from 'lucide-react';
import { cn } from '@/lib/utils';

export type SelectMenuOption = { value: string; label: string };

type SelectMenuProps = {
  value: string;
  onValueChange: (value: string) => void;
  options: SelectMenuOption[];
  placeholder?: string;
  disabled?: boolean;
  className?: string;
  /** Max height of the dropdown list (scroll) */
  menuMaxHeightClass?: string;
  emptyMessage?: string;
};

export function SelectMenu({
  value,
  onValueChange,
  options,
  placeholder = '请选择…',
  disabled = false,
  className,
  menuMaxHeightClass = 'max-h-60',
  emptyMessage = '暂无选项',
}: SelectMenuProps) {
  const [open, setOpen] = useState(false);
  const rootRef = useRef<HTMLDivElement>(null);
  const listId = useId();

  const selected = options.find((o) => o.value === value);
  const label = selected?.label ?? placeholder;

  const close = useCallback(() => setOpen(false), []);

  useEffect(() => {
    if (!open) return;
    const onDoc = (e: MouseEvent) => {
      if (!rootRef.current?.contains(e.target as Node)) close();
    };
    const onKey = (e: KeyboardEvent) => {
      if (e.key === 'Escape') close();
    };
    document.addEventListener('mousedown', onDoc);
    document.addEventListener('keydown', onKey);
    return () => {
      document.removeEventListener('mousedown', onDoc);
      document.removeEventListener('keydown', onKey);
    };
  }, [open, close]);

  const handlePick = (v: string) => {
    onValueChange(v);
    close();
  };

  return (
    <div ref={rootRef} className={cn('relative', className)}>
      <button
        type="button"
        disabled={disabled}
        aria-haspopup="listbox"
        aria-expanded={open}
        aria-controls={listId}
        onClick={() => !disabled && setOpen((o) => !o)}
        title={label}
        className={cn(
          'flex w-full min-w-0 items-center justify-between gap-2 rounded-lg border border-slate-200 bg-white px-3 py-2 text-left text-sm text-slate-800 shadow-sm transition-colors',
          'hover:border-slate-300 hover:bg-slate-50/80',
          'focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-indigo-500/25 focus-visible:border-indigo-400',
          disabled && 'cursor-not-allowed opacity-50 hover:border-slate-200 hover:bg-white',
          open && 'border-indigo-300 ring-2 ring-indigo-500/20'
        )}
      >
        <span className={cn('truncate', !selected && 'text-slate-400')}>{label}</span>
        <ChevronDown
          className={cn('h-4 w-4 shrink-0 text-slate-500 transition-transform duration-200', open && 'rotate-180')}
          aria-hidden
        />
      </button>

      {open && (
        <div
          id={listId}
          role="listbox"
          className={cn(
            'absolute z-50 mt-1.5 min-w-full w-max max-w-[min(calc(100vw-2rem),20rem)] overflow-hidden rounded-xl border border-slate-200/80 bg-white py-1 shadow-lg shadow-slate-200/50 ring-1 ring-slate-900/5',
            menuMaxHeightClass,
            'overflow-y-auto overscroll-contain'
          )}
        >
          {options.length === 0 ? (
            <div className="px-3 py-2.5 text-sm text-slate-400">{emptyMessage}</div>
          ) : (
            options.map((opt) => {
              const isActive = opt.value === value;
              return (
                <button
                  key={opt.value}
                  type="button"
                  role="option"
                  aria-selected={isActive}
                  onClick={() => handlePick(opt.value)}
                  className={cn(
                    'flex w-full items-center gap-2 px-3 py-2 text-left text-sm transition-colors',
                    isActive
                      ? 'bg-indigo-50 text-indigo-900'
                      : 'text-slate-700 hover:bg-slate-50'
                  )}
                >
                  <span className="min-w-0 flex-1 truncate" title={opt.label}>
                    {opt.label}
                  </span>
                  {isActive && <Check className="h-4 w-4 shrink-0 text-indigo-600" aria-hidden />}
                </button>
              );
            })
          )}
        </div>
      )}
    </div>
  );
}
