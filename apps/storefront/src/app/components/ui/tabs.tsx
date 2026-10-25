"use client";

import * as React from "react";
import * as TabsPrimitive from "@radix-ui/react-tabs";
import { motion } from "motion/react";
import { spring } from "./animate-tokens";
import { cn } from "./utils";

const TabsContext = React.createContext<{
  activeTab?: string;
  setActiveTab?: (val: string) => void;
}>({});

function Tabs({
  className,
  value,
  defaultValue,
  onValueChange,
  children,
  ...props
}: React.ComponentProps<typeof TabsPrimitive.Root>) {
  const [internalValue, setInternalValue] = React.useState(defaultValue || "");
  const activeValue = value !== undefined ? value : internalValue;

  const handleValueChange = (val: string) => {
    if (value === undefined) setInternalValue(val);
    onValueChange?.(val);
  };

  return (
    <TabsContext.Provider value={{ activeTab: activeValue, setActiveTab: handleValueChange }}>
      <TabsPrimitive.Root
        data-slot="tabs"
        value={value}
        defaultValue={defaultValue}
        onValueChange={handleValueChange}
        className={cn("flex flex-col gap-2", className)}
        {...props}
      >
        {children}
      </TabsPrimitive.Root>
    </TabsContext.Provider>
  );
}

function TabsList({
  className,
  ...props
}: React.ComponentProps<typeof TabsPrimitive.List>) {
  return (
    <TabsPrimitive.List
      data-slot="tabs-list"
      className={cn(
        "bg-muted text-muted-foreground inline-flex h-10 w-fit items-center justify-center rounded-xl p-1 relative",
        className,
      )}
      {...props}
    />
  );
}

function TabsTrigger({
  className,
  value,
  children,
  ...props
}: React.ComponentProps<typeof TabsPrimitive.Trigger>) {
  const { activeTab } = React.useContext(TabsContext);
  const isActive = activeTab === value;

  return (
    <TabsPrimitive.Trigger
      data-slot="tabs-trigger"
      value={value}
      className={cn(
        "relative text-muted-foreground data-[state=active]:text-foreground focus-visible:ring-ring/50 inline-flex h-full flex-1 items-center justify-center gap-1.5 rounded-lg px-3 py-1.5 text-sm font-medium whitespace-nowrap transition-colors focus-visible:ring-[3px] focus-visible:outline-none disabled:pointer-events-none disabled:opacity-50",
        className,
      )}
      {...props}
    >
      {isActive && (
        <motion.span
          layoutId="active-tab-indicator"
          transition={spring.selection}
          className="absolute inset-0 rounded-lg bg-card shadow-xs dark:bg-input/40 border border-border/50"
          style={{ zIndex: 0 }}
        />
      )}
      <span className="relative z-10 flex items-center gap-1.5">{children}</span>
    </TabsPrimitive.Trigger>
  );
}

function TabsContent({
  className,
  ...props
}: React.ComponentProps<typeof TabsPrimitive.Content>) {
  return (
    <TabsPrimitive.Content
      data-slot="tabs-content"
      className={cn("flex-1 outline-none", className)}
      {...props}
    />
  );
}

export { Tabs, TabsList, TabsTrigger, TabsContent };
