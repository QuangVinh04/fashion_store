import {
  Children,
  isValidElement,
  useId,
  useState,
  useRef,
  type ReactNode,
} from "react";
import { Check, ChevronsUpDown } from "lucide-react";
import {
  Popover,
  PopoverContent,
  PopoverTrigger,
} from "../app/components/ui/popover";
import {
  Command,
  CommandInput,
  CommandList,
  CommandEmpty,
  CommandItem,
} from "../app/components/ui/command";
import { cn } from "../app/components/ui/utils";

type Props = {
  value: string;
  onChange: (event: { target: { value: string } }) => void;
  children: ReactNode;
  id?: string;
  name?: string;
  required?: boolean;
  disabled?: boolean;
  className?: string;
  "aria-label"?: string;
};

/** Presentation adapter: callers keep their original values and change handlers. */
export default function StoreSelect({
  value,
  onChange,
  children,
  id,
  name,
  required,
  disabled,
  className,
  "aria-label": label,
}: Props) {
  const [open, setOpen] = useState(false);
  const generatedId = useId();
  const listRef = useRef<HTMLDivElement>(null);
  const options = Children.toArray(children)
    .filter(isValidElement)
    .map((child) => {
      const props = child.props as {
        value?: string | number;
        children?: ReactNode;
        disabled?: boolean;
      };
      return {
        value: String(props.value ?? ""),
        label: props.children,
        disabled: props.disabled,
      };
    });
  const selected = options.find((option) => option.value === value);
  return (
    <div className="relative min-w-0">
      <select
        name={name}
        required={required}
        disabled={disabled}
        value={value}
        tabIndex={-1}
        aria-hidden="true"
        className="sr-only"
        onChange={onChange}
        onInvalid={() => setOpen(true)}
      >
        {children}
      </select>
      <Popover open={open} onOpenChange={setOpen}>
        <PopoverTrigger asChild>
          <button
            type="button"
            id={id || generatedId}
            role="combobox"
            aria-label={label}
            aria-expanded={open}
            aria-required={required}
            disabled={disabled}
            className={cn(
              "flex h-11 w-full min-w-0 items-center justify-between gap-3 rounded-xl border border-border-strong bg-card px-3 text-left text-sm text-foreground disabled:cursor-not-allowed disabled:bg-background disabled:text-muted-foreground md:h-10",
              className,
            )}
          >
            <span className="truncate">{selected?.label}</span>
            <ChevronsUpDown
              size={15}
              className="shrink-0 text-muted-foreground"
            />
          </button>
        </PopoverTrigger>
        <PopoverContent
          align="start"
          onOpenAutoFocus={(event) => {
            if (options.length < 8) {
              event.preventDefault();
              listRef.current?.focus();
            }
          }}
          className="w-[var(--radix-popover-trigger-width)] rounded-xl border-border p-1.5 shadow-lg"
        >
          <Command
            label={label}
            defaultValue={`${options.findIndex((option) => option.value === value)} ${selected?.label}`}
            onFocusCapture={(event) => {
              // cmdk seeds defaultValue before it publishes the initial active item ID.
              const target = event.target as HTMLElement;
              const active = event.currentTarget.querySelector<HTMLElement>(
                '[cmdk-item][data-selected="true"]',
              );
              if (
                active &&
                ["listbox", "combobox"].includes(
                  target.getAttribute("role") || "",
                ) &&
                !target.getAttribute("aria-activedescendant")
              ) {
                target.setAttribute("aria-activedescendant", active.id);
              }
            }}
          >
            {options.length >= 8 && <CommandInput placeholder="Tìm kiếm…" />}
            <CommandList
              ref={listRef}
              tabIndex={-1}
              label={label}
              className="max-h-64"
            >
              <CommandEmpty>Không tìm thấy kết quả.</CommandEmpty>
              {options.map((option, index) => (
                <CommandItem
                  key={option.value || `empty-${index}`}
                  value={`${index} ${option.label}`}
                  disabled={option.disabled}
                  className="min-h-10 rounded-lg text-sm data-[selected=true]:bg-background"
                  onSelect={() => {
                    onChange({ target: { value: option.value } });
                    setOpen(false);
                  }}
                >
                  <span className="flex-1">{option.label}</span>
                  <Check
                    size={15}
                    className={
                      value === option.value ? "opacity-100" : "opacity-0"
                    }
                  />
                </CommandItem>
              ))}
            </CommandList>
          </Command>
        </PopoverContent>
      </Popover>
    </div>
  );
}
