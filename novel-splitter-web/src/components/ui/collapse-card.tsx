import { useState } from 'react';
import { Card, CardHeader, CardTitle, CardContent } from '@/components/ui/card';
import { ChevronDown, ChevronUp } from 'lucide-react';
import { cn } from '@/lib/utils';

export function CollapseCard({ title, children, defaultOpen = true, extra, className }: { title: React.ReactNode, children: React.ReactNode, defaultOpen?: boolean, extra?: React.ReactNode, className?: string }) {
  const [isOpen, setIsOpen] = useState(defaultOpen);

  return (
    <Card className={cn("relative mb-4", className)}>
      <CardHeader className="flex flex-row items-center justify-between pb-2 py-3 cursor-pointer select-none" onClick={() => setIsOpen(!isOpen)}>
        <CardTitle className="text-md flex items-center gap-2">
          {isOpen ? <ChevronUp className="w-4 h-4" /> : <ChevronDown className="w-4 h-4" />}
          {title}
        </CardTitle>
        <div className="flex items-center gap-2" onClick={(e) => e.stopPropagation()}>
          {extra}
        </div>
      </CardHeader>
      {isOpen && (
        <CardContent className="pt-0">
          {children}
        </CardContent>
      )}
    </Card>
  );
}
