import React, { useEffect, useRef, useState, useMemo } from "react";
import {
  Share2,
  Search,
  RefreshCw,
  ZoomIn,
  ZoomOut,
  Maximize2,
  Layers,
  Database,
  MessageSquare,
  FileText,
  Code2,
  Globe,
  X,
  ExternalLink,
  ChevronRight,
  Info,
} from "lucide-react";
import {
  getNeuralGraphTopology,
  type GraphTopology,
  type GraphNode,
  type GraphEdge,
} from "@/api";

interface NeuralGraphPanelProps {
  token: string;
  onNavigate?: (view: any) => void;
}

interface SimNode extends GraphNode {
  x: number;
  y: number;
  vx: number;
  vy: number;
  radius: number;
}

interface SimEdge {
  source: SimNode;
  target: SimNode;
  weight: number;
  relationship: string;
}

const COLOR_MAP: Record<string, string> = {
  CHAT: "#a855f7",         // Violet
  MEMORY: "#10b981",       // Emerald
  DOCUMENT: "#06b6d4",     // Cyan
  CODE_SESSION: "#f59e0b", // Amber
  CODE_FILE: "#3b82f6",    // Blue
  LEARNED_FACT: "#f43f5e", // Rose
};

export default function NeuralGraphPanel({ token, onNavigate }: NeuralGraphPanelProps) {
  const canvasRef = useRef<HTMLCanvasElement | null>(null);

  const [topology, setTopology] = useState<GraphTopology | null>(null);
  const [loading, setLoading] = useState(false);
  const [searchQuery, setSearchQuery] = useState("");
  const [selectedNode, setSelectedNode] = useState<SimNode | null>(null);

  // Category filter state (all enabled by default)
  const [activeFilters, setActiveFilters] = useState<Record<string, boolean>>({
    CHAT: true,
    MEMORY: true,
    DOCUMENT: true,
    CODE_SESSION: true,
    CODE_FILE: true,
    LEARNED_FACT: true,
  });

  // Physics simulation state
  const simNodesRef = useRef<SimNode[]>([]);
  const simEdgesRef = useRef<SimEdge[]>([]);
  const animationFrameRef = useRef<number | null>(null);

  // Pan and zoom
  const [zoom, setZoom] = useState(1.0);
  const [pan, setPan] = useState({ x: 0, y: 0 });
  const isDraggingCanvasRef = useRef(false);
  const isDraggingNodeRef = useRef<SimNode | null>(null);
  const lastMousePosRef = useRef({ x: 0, y: 0 });

  useEffect(() => {
    void loadGraph();
  }, [token]);

  async function loadGraph() {
    setLoading(true);
    try {
      const data = await getNeuralGraphTopology("", 200, token);
      setTopology(data);
      initSimulation(data);
    } catch (e) {
      console.warn("Failed to load neural graph topology:", e);
    } finally {
      setLoading(false);
    }
  }

  function initSimulation(data: GraphTopology) {
    const canvas = canvasRef.current;
    const width = canvas ? canvas.clientWidth : 1000;
    const height = canvas ? canvas.clientHeight : 700;

    const nodeMap = new Map<string, SimNode>();
    const nodes: SimNode[] = data.nodes.map((n, i) => {
      // Circular distribution for pleasant initial layout
      const angle = (i / Math.max(1, data.nodes.length)) * 2 * Math.PI;
      const radius = 150 + Math.random() * 200;
      const simNode: SimNode = {
        ...n,
        x: width / 2 + Math.cos(angle) * radius,
        y: height / 2 + Math.sin(angle) * radius,
        vx: (Math.random() - 0.5) * 0.5,
        vy: (Math.random() - 0.5) * 0.5,
        radius: Math.max(5, Math.min(n.size || 10, 20)),
      };
      nodeMap.set(n.id, simNode);
      return simNode;
    });

    const edges: SimEdge[] = [];
    for (const e of data.edges) {
      const src = nodeMap.get(e.source);
      const tgt = nodeMap.get(e.target);
      if (src && tgt) {
        edges.push({
          source: src,
          target: tgt,
          weight: e.weight || 0.5,
          relationship: e.relationship,
        });
      }
    }

    simNodesRef.current = nodes;
    simEdgesRef.current = edges;
  }

  // Animation & Force Physics Loop
  useEffect(() => {
    const canvas = canvasRef.current;
    if (!canvas) return;
    const ctx = canvas.getContext("2d");
    if (!ctx) return;

    let running = true;

    function renderLoop() {
      if (!running) return;

      const width = canvas!.clientWidth;
      const height = canvas!.clientHeight;
      if (canvas!.width !== width || canvas!.height !== height) {
        canvas!.width = width;
        canvas!.height = height;
      }

      // 1. Force Physics Step
      updatePhysics(width, height);

      // 2. Draw Frame
      ctx!.clearRect(0, 0, width, height);

      ctx!.save();
      // Apply Pan and Zoom
      ctx!.translate(pan.x + width / 2, pan.y + height / 2);
      ctx!.scale(zoom, zoom);
      ctx!.translate(-width / 2, -height / 2);

      drawSynapseEdges(ctx!);
      drawNeuralNodes(ctx!);

      ctx!.restore();

      animationFrameRef.current = requestAnimationFrame(renderLoop);
    }

    animationFrameRef.current = requestAnimationFrame(renderLoop);

    return () => {
      running = false;
      if (animationFrameRef.current) cancelAnimationFrame(animationFrameRef.current);
    };
  }, [pan, zoom, activeFilters, searchQuery, selectedNode]);

  function updatePhysics(width: number, height: number) {
    const nodes = simNodesRef.current;
    const edges = simEdgesRef.current;

    // Node Repulsion
    for (let i = 0; i < nodes.length; i++) {
      const n1 = nodes[i];
      if (!activeFilters[n1.type]) continue;

      for (let j = i + 1; j < nodes.length; j++) {
        const n2 = nodes[j];
        if (!activeFilters[n2.type]) continue;

        const dx = n2.x - n1.x;
        const dy = n2.y - n1.y;
        const distSq = dx * dx + dy * dy + 1;
        const dist = Math.sqrt(distSq);

        if (dist < 250) {
          const force = (250 - dist) / 250;
          const fx = (dx / dist) * force * 1.2;
          const fy = (dy / dist) * force * 1.2;

          if (isDraggingNodeRef.current !== n1) {
            n1.vx -= fx;
            n1.vy -= fy;
          }
          if (isDraggingNodeRef.current !== n2) {
            n2.vx += fx;
            n2.vy += fy;
          }
        }
      }

      // Center Gravity
      const cx = width / 2;
      const cy = height / 2;
      const cdx = cx - n1.x;
      const cdy = cy - n1.y;
      n1.vx += cdx * 0.0008;
      n1.vy += cdy * 0.0008;
    }

    // Edge Spring Attraction
    for (const edge of edges) {
      if (!activeFilters[edge.source.type] || !activeFilters[edge.target.type]) continue;

      const dx = edge.target.x - edge.source.x;
      const dy = edge.target.y - edge.source.y;
      const dist = Math.sqrt(dx * dx + dy * dy) + 0.1;
      const targetDist = 90 / (edge.weight || 1);
      const force = (dist - targetDist) * 0.008 * edge.weight;

      const fx = (dx / dist) * force;
      const fy = (dy / dist) * force;

      if (isDraggingNodeRef.current !== edge.source) {
        edge.source.vx += fx;
        edge.source.vy += fy;
      }
      if (isDraggingNodeRef.current !== edge.target) {
        edge.target.vx -= fx;
        edge.target.vy -= fy;
      }
    }

    // Velocity update & damping
    for (const node of nodes) {
      if (isDraggingNodeRef.current === node) continue;
      node.x += node.vx;
      node.y += node.vy;
      node.vx *= 0.88; // Damping
      node.vy *= 0.88;
    }
  }

  function drawSynapseEdges(ctx: CanvasRenderingContext2D) {
    const edges = simEdgesRef.current;
    const isSearching = searchQuery.trim().length > 0;

    for (const edge of edges) {
      if (!activeFilters[edge.source.type] || !activeFilters[edge.target.type]) continue;

      const isConnectedToSelected = selectedNode && (selectedNode.id === edge.source.id || selectedNode.id === edge.target.id);
      const alpha = isConnectedToSelected ? 0.85 : isSearching ? 0.12 : 0.25;

      ctx.beginPath();
      ctx.moveTo(edge.source.x, edge.source.y);
      ctx.lineTo(edge.target.x, edge.target.y);

      if (isConnectedToSelected) {
        ctx.strokeStyle = "#06b6d4";
        ctx.lineWidth = 2.0;
      } else {
        ctx.strokeStyle = `rgba(148, 163, 184, ${alpha})`;
        ctx.lineWidth = 1.0;
      }
      ctx.stroke();
    }
  }

  function drawNeuralNodes(ctx: CanvasRenderingContext2D) {
    const nodes = simNodesRef.current;
    const query = searchQuery.trim().toLowerCase();

    for (const node of nodes) {
      if (!activeFilters[node.type]) continue;

      const isMatched = query ? node.label.toLowerCase().includes(query) : true;
      const isSelected = selectedNode?.id === node.id;
      const color = COLOR_MAP[node.type] || "#94a3b8";

      ctx.save();

      // Outer glow for selected or searched nodes
      if (isSelected || (query && isMatched)) {
        ctx.beginPath();
        ctx.arc(node.x, node.y, node.radius + 6, 0, Math.PI * 2);
        ctx.fillStyle = `${color}33`;
        ctx.fill();

        ctx.beginPath();
        ctx.arc(node.x, node.y, node.radius + 3, 0, Math.PI * 2);
        ctx.strokeStyle = color;
        ctx.lineWidth = 2;
        ctx.stroke();
      }

      // Main Node Body
      ctx.beginPath();
      ctx.arc(node.x, node.y, node.radius, 0, Math.PI * 2);
      ctx.fillStyle = query && !isMatched ? "#1e293b" : color;
      ctx.fill();

      // Center Core
      ctx.beginPath();
      ctx.arc(node.x, node.y, node.radius * 0.45, 0, Math.PI * 2);
      ctx.fillStyle = "#ffffff";
      ctx.fill();

      // Label
      if (zoom > 0.75 || isSelected || (query && isMatched)) {
        ctx.font = isSelected ? "bold 11px system-ui" : "10px system-ui";
        ctx.fillStyle = isSelected ? "#ffffff" : "#cbd5e1";
        ctx.textAlign = "center";
        const label = node.label.length > 20 ? node.label.substring(0, 18) + ".." : node.label;
        ctx.fillText(label, node.x, node.y + node.radius + 12);
      }

      ctx.restore();
    }
  }

  // Mouse Interaction Handlers
  function handleMouseDown(e: React.MouseEvent<HTMLCanvasElement>) {
    const canvas = canvasRef.current;
    if (!canvas) return;
    const rect = canvas.getBoundingClientRect();
    const mouseX = e.clientX - rect.left;
    const mouseY = e.clientY - rect.top;

    lastMousePosRef.current = { x: mouseX, y: mouseY };

    // Convert mouse to simulation coordinates
    const worldX = (mouseX - (pan.x + canvas.clientWidth / 2)) / zoom + canvas.clientWidth / 2;
    const worldY = (mouseY - (pan.y + canvas.clientHeight / 2)) / zoom + canvas.clientHeight / 2;

    // Check if clicked a node
    const nodes = simNodesRef.current;
    let clicked: SimNode | null = null;
    for (let i = nodes.length - 1; i >= 0; i--) {
      const n = nodes[i];
      if (!activeFilters[n.type]) continue;
      const dx = worldX - n.x;
      const dy = worldY - n.y;
      if (dx * dx + dy * dy <= (n.radius + 4) * (n.radius + 4)) {
        clicked = n;
        break;
      }
    }

    if (clicked) {
      isDraggingNodeRef.current = clicked;
      setSelectedNode(clicked);
    } else {
      isDraggingCanvasRef.current = true;
      if (!e.shiftKey) setSelectedNode(null);
    }
  }

  function handleMouseMove(e: React.MouseEvent<HTMLCanvasElement>) {
    const canvas = canvasRef.current;
    if (!canvas) return;
    const rect = canvas.getBoundingClientRect();
    const mouseX = e.clientX - rect.left;
    const mouseY = e.clientY - rect.top;

    const dx = mouseX - lastMousePosRef.current.x;
    const dy = mouseY - lastMousePosRef.current.y;
    lastMousePosRef.current = { x: mouseX, y: mouseY };

    if (isDraggingNodeRef.current) {
      isDraggingNodeRef.current.x += dx / zoom;
      isDraggingNodeRef.current.y += dy / zoom;
      isDraggingNodeRef.current.vx = 0;
      isDraggingNodeRef.current.vy = 0;
    } else if (isDraggingCanvasRef.current) {
      setPan((prev) => ({ x: prev.x + dx, y: prev.y + dy }));
    }
  }

  function handleMouseUp() {
    isDraggingNodeRef.current = null;
    isDraggingCanvasRef.current = false;
  }

  function handleWheel(e: React.WheelEvent<HTMLCanvasElement>) {
    e.preventDefault();
    const factor = e.deltaY < 0 ? 1.1 : 0.9;
    setZoom((prev) => Math.max(0.3, Math.min(prev * factor, 3.0)));
  }

  function toggleCategory(cat: string) {
    setActiveFilters((prev) => ({ ...prev, [cat]: !prev[cat] }));
  }

  // Get connected neighbors for selected node
  const selectedNeighbors = useMemo(() => {
    if (!selectedNode || !topology) return [];
    const connectedIds = new Set<string>();
    for (const edge of topology.edges) {
      if (edge.source === selectedNode.id) connectedIds.add(edge.target);
      if (edge.target === selectedNode.id) connectedIds.add(edge.source);
    }
    return topology.nodes.filter((n) => connectedIds.has(n.id));
  }, [selectedNode, topology]);

  return (
    <div className="h-full flex flex-col bg-[#080c14] text-slate-200 overflow-hidden font-sans relative">
      {/* Top Controls Toolbar */}
      <header className="px-6 py-3 border-b border-slate-800/80 bg-[#0d131f]/90 flex items-center justify-between shrink-0 z-10 backdrop-blur-md">
        <div className="flex items-center gap-3">
          <div className="w-8 h-8 rounded-lg bg-purple-500/10 border border-purple-500/30 flex items-center justify-center text-purple-400">
            <Share2 size={18} />
          </div>
          <div>
            <div className="flex items-center gap-2">
              <h1 className="text-sm font-semibold tracking-wide text-white uppercase">Neural Knowledge Graph</h1>
              <span className="text-[10px] px-2 py-0.5 rounded-full bg-purple-500/10 text-purple-400 border border-purple-500/20 font-mono">
                OBSIDIAN SYNAPSE VIEW
              </span>
            </div>
            <p className="text-xs text-slate-400">
              Visualizing synaptic connections across persistent DB memories, chats, files, and coding sessions.
            </p>
          </div>
        </div>

        {/* Filter & Search Bar */}
        <div className="flex items-center gap-3">
          {/* Search Bar */}
          <div className="flex items-center gap-1.5 px-2.5 py-1 bg-slate-900 border border-slate-800 rounded-md text-xs text-slate-400 w-56">
            <Search size={13} className="text-slate-500 shrink-0" />
            <input
              type="text"
              placeholder="Highlight nodes & clusters..."
              value={searchQuery}
              onChange={(e) => setSearchQuery(e.target.value)}
              className="w-full bg-transparent focus:outline-none text-slate-200 text-xs placeholder:text-slate-500"
            />
          </div>

          {/* Zoom Controls */}
          <div className="flex items-center gap-1 bg-slate-900 border border-slate-800 rounded-md p-0.5">
            <button
              onClick={() => setZoom((z) => Math.min(z * 1.2, 3.0))}
              className="p-1 hover:bg-slate-800 rounded text-slate-400 hover:text-slate-200"
              title="Zoom In"
            >
              <ZoomIn size={14} />
            </button>
            <button
              onClick={() => setZoom((z) => Math.max(z * 0.8, 0.3))}
              className="p-1 hover:bg-slate-800 rounded text-slate-400 hover:text-slate-200"
              title="Zoom Out"
            >
              <ZoomOut size={14} />
            </button>
            <button
              onClick={() => {
                setZoom(1.0);
                setPan({ x: 0, y: 0 });
              }}
              className="p-1 hover:bg-slate-800 rounded text-slate-400 hover:text-slate-200"
              title="Reset View"
            >
              <Maximize2 size={14} />
            </button>
          </div>

          <button
            onClick={loadGraph}
            disabled={loading}
            className="p-1.5 hover:bg-slate-800 rounded-md text-slate-400 hover:text-slate-200 transition-colors border border-slate-800"
            title="Refresh Knowledge Graph"
          >
            <RefreshCw size={14} className={loading ? "animate-spin" : ""} />
          </button>
        </div>
      </header>

      {/* Category Filter Pills */}
      <div className="px-6 py-2 bg-[#0a0e17] border-b border-slate-800/40 flex items-center gap-2 overflow-x-auto text-xs z-10">
        <span className="text-[11px] text-slate-500 uppercase tracking-wider font-semibold mr-1">Entities:</span>

        {Object.entries(COLOR_MAP).map(([type, color]) => {
          const active = activeFilters[type];
          const count = topology?.categoryCounts[type] || 0;
          return (
            <button
              key={type}
              onClick={() => toggleCategory(type)}
              style={{
                borderColor: active ? `${color}60` : "transparent",
                backgroundColor: active ? `${color}15` : "#0f172a",
                color: active ? color : "#64748b",
              }}
              className="px-2.5 py-0.5 rounded-full border text-[11px] font-medium transition-colors flex items-center gap-1.5"
            >
              <span className="w-2 h-2 rounded-full" style={{ backgroundColor: color }} />
              <span>{type.replace("_", " ")}</span>
              <span className="text-[10px] opacity-75 font-mono">({count})</span>
            </button>
          );
        })}
      </div>

      {/* Canvas Area */}
      <div className="flex-1 relative overflow-hidden bg-[#070a10]">
        <canvas
          ref={canvasRef}
          onMouseDown={handleMouseDown}
          onMouseMove={handleMouseMove}
          onMouseUp={handleMouseUp}
          onWheel={handleWheel}
          className="w-full h-full cursor-grab active:cursor-grabbing block"
        />

        {/* Node Inspection Drawer */}
        {selectedNode && (
          <aside className="absolute right-0 top-0 bottom-0 w-80 bg-[#0d131f]/95 border-l border-slate-800 p-4 shadow-2xl backdrop-blur-md z-20 flex flex-col overflow-hidden animate-in slide-in-from-right duration-200">
            <div className="flex items-center justify-between pb-3 border-b border-slate-800">
              <div className="flex items-center gap-2">
                <span
                  className="w-3 h-3 rounded-full"
                  style={{ backgroundColor: COLOR_MAP[selectedNode.type] || "#94a3b8" }}
                />
                <span className="text-xs font-mono font-bold text-slate-400 uppercase">{selectedNode.type}</span>
              </div>
              <button
                onClick={() => setSelectedNode(null)}
                className="text-slate-400 hover:text-white p-1 rounded hover:bg-slate-800"
              >
                <X size={15} />
              </button>
            </div>

            <div className="flex-1 overflow-y-auto py-3 space-y-4 text-xs">
              <div>
                <h3 className="text-sm font-semibold text-white mb-1">{selectedNode.label}</h3>
                <p className="text-slate-500 text-[11px] font-mono">ID: {selectedNode.id}</p>
              </div>

              {/* Metadata content */}
              {selectedNode.metadata && (
                <div className="p-3 rounded-lg bg-slate-900 border border-slate-800 space-y-2">
                  <span className="text-[11px] font-semibold text-slate-400 block uppercase tracking-wider">
                    Details &amp; Excerpt
                  </span>
                  <p className="text-slate-300 whitespace-pre-wrap text-[11px] font-mono max-h-48 overflow-y-auto">
                    {String(
                      selectedNode.metadata.content ||
                      selectedNode.metadata.objective ||
                      selectedNode.metadata.answer ||
                      selectedNode.metadata.path ||
                      "No further metadata."
                    )}
                  </p>
                </div>
              )}

              {/* Connected Synaptic Neighbors */}
              <div>
                <span className="text-[11px] font-semibold text-slate-400 block uppercase tracking-wider mb-2">
                  Connected Synapses ({selectedNeighbors.length})
                </span>
                <div className="space-y-1.5 max-h-60 overflow-y-auto">
                  {selectedNeighbors.map((nb) => (
                    <div
                      key={nb.id}
                      onClick={() => {
                        const targetSim = simNodesRef.current.find((s) => s.id === nb.id);
                        if (targetSim) setSelectedNode(targetSim);
                      }}
                      className="p-2 rounded bg-slate-900/60 hover:bg-slate-800/80 border border-slate-800/60 cursor-pointer flex items-center justify-between transition-colors"
                    >
                      <div className="flex items-center gap-2 truncate">
                        <span
                          className="w-2 h-2 rounded-full shrink-0"
                          style={{ backgroundColor: COLOR_MAP[nb.type] || "#94a3b8" }}
                        />
                        <span className="text-slate-200 text-xs truncate">{nb.label}</span>
                      </div>
                      <ChevronRight size={13} className="text-slate-500 shrink-0" />
                    </div>
                  ))}
                </div>
              </div>

              {/* Quick Navigation Action */}
              {onNavigate && (
                <div className="pt-2 border-t border-slate-800">
                  {selectedNode.type === "CODE_SESSION" || selectedNode.type === "CODE_FILE" ? (
                    <button
                      onClick={() => onNavigate("coding")}
                      className="w-full py-2 px-3 rounded-md bg-blue-600/20 hover:bg-blue-600/30 text-blue-400 border border-blue-500/30 flex items-center justify-center gap-2 text-xs font-medium transition-colors"
                    >
                      <Code2 size={13} />
                      <span>Open in Coding Studio</span>
                    </button>
                  ) : selectedNode.type === "CHAT" ? (
                    <button
                      onClick={() => onNavigate("assistant")}
                      className="w-full py-2 px-3 rounded-md bg-purple-600/20 hover:bg-purple-600/30 text-purple-400 border border-purple-500/30 flex items-center justify-center gap-2 text-xs font-medium transition-colors"
                    >
                      <MessageSquare size={13} />
                      <span>Open in Chat Assistant</span>
                    </button>
                  ) : selectedNode.type === "MEMORY" ? (
                    <button
                      onClick={() => onNavigate("database_memory")}
                      className="w-full py-2 px-3 rounded-md bg-emerald-600/20 hover:bg-emerald-600/30 text-emerald-400 border border-emerald-500/30 flex items-center justify-center gap-2 text-xs font-medium transition-colors"
                    >
                      <Database size={13} />
                      <span>Inspect in Database Memories</span>
                    </button>
                  ) : null}
                </div>
              )}
            </div>
          </aside>
        )}
      </div>
    </div>
  );
}
