"use client";

import { useEffect, useRef, useState } from "react";
import { animate } from "motion";

interface CountingNumberProps {
  value: number | string;
  format?: (val: string) => string;
  className?: string;
}

export function CountingNumber({
  value,
  format = (v) => Number(v).toLocaleString("vi-VN"),
  className = "",
}: CountingNumberProps) {
  const numValue = typeof value === "string" ? parseFloat(value) || 0 : value;
  const [displayValue, setDisplayValue] = useState(String(numValue));
  const prevValue = useRef(numValue);

  useEffect(() => {
    const controls = animate(prevValue.current, numValue, {
      type: "spring",
      stiffness: 90,
      damping: 20,
      onUpdate: (latest) => {
        setDisplayValue(String(Math.round(latest)));
      },
    });

    prevValue.current = numValue;
    return () => controls.stop();
  }, [numValue]);

  return <span className={className}>{format(displayValue)}</span>;
}
