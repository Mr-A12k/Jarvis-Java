import type { LucideIcon } from "lucide-react";

export type ActiveView =
  | "assistant"
  | "voice"
  | "desktop"
  | "smarthome"
  | "saved_chats"
  | "database_memory"
  | "files"
  | "shopping"
  | "automation"
  | "scheduler"
  | "settings"
  | "biometrics"
  | "goals"
  | "coding"
  | "graph";

export interface NavItemDef {
  id: ActiveView;
  label: string;
  icon: LucideIcon;
  badge?: number | string;
  requiresConnection?: boolean;
}
