import React, { useEffect, useRef, useState, memo } from 'react';
import * as THREE from 'three';
import { Send, Mic, Square, X, Globe, Sparkles } from 'lucide-react';

export interface JarvisHologramProps {
  state: 'idle' | 'listening' | 'thinking' | 'speaking';
  lang?: 'en-US' | 'ta-IN';
  userTranscript?: string;
  jarvisReply?: string;
  mode?: 'fullscreen' | 'embedded';
  onClose?: () => void;
  onExpand?: () => void;
  onMicClick?: () => void;
  onSendText?: (text: string) => void;
  isContinuous?: boolean;
  onContinuousChange?: (val: boolean) => void;
  onLangChange?: (lang: 'en-US' | 'ta-IN') => void;
  voiceRate?: number;
  onVoiceRateChange?: (rate: number) => void;
  telemetry?: {
    ram?: string;
    gpu?: string;
    rom?: string;
    model?: string;
  };
}

const JarvisHologram = memo(function JarvisHologram({
  state,
  lang = 'en-US',
  userTranscript = '',
  jarvisReply = '',
  mode = 'fullscreen',
  onClose,
  onExpand,
  onMicClick,
  onSendText,
  isContinuous = false,
  onContinuousChange,
  onLangChange,
  voiceRate = 1.0,
  onVoiceRateChange,
  telemetry,
}: JarvisHologramProps) {
  const mountRef = useRef<HTMLDivElement>(null);
  const stateRef = useRef(state);
  stateRef.current = state;
  const [textDraft, setTextDraft] = useState('');
  const [activeTab, setActiveTab] = useState<'voice' | 'text'>('voice');

  useEffect(() => {
    const container = mountRef.current;
    if (!container) return;

    let width = container.clientWidth || (mode === 'embedded' ? 380 : window.innerWidth);
    let height = container.clientHeight || (mode === 'embedded' ? 260 : window.innerHeight);

    // 1. Scene, Camera, Renderer
    const scene = new THREE.Scene();
    if (mode === 'fullscreen') {
      scene.fog = new THREE.FogExp2(0x050a12, 0.015);
    }

    const camera = new THREE.PerspectiveCamera(52, width / height, 0.1, 1000);
    camera.position.z = mode === 'embedded' ? 22 : 18;

    const renderer = new THREE.WebGLRenderer({ antialias: true, alpha: true });
    renderer.setSize(width, height);
    renderer.setPixelRatio(Math.min(window.devicePixelRatio, 2));
    renderer.setClearColor(0x000000, 0);
    container.appendChild(renderer.domElement);

    // 2. Lights
    const ambientLight = new THREE.AmbientLight(0x00e1ff, 0.7);
    scene.add(ambientLight);

    const coreLight = new THREE.PointLight(0x00ffff, 4, 35);
    scene.add(coreLight);

    const amberLight = new THREE.PointLight(0xffa200, 2, 25);
    amberLight.position.set(5, 5, 5);
    scene.add(amberLight);

    // 3. Central Arc Reactor Core
    const coreGroup = new THREE.Group();
    scene.add(coreGroup);

    // Inner wireframe icosahedron
    const icoGeo = new THREE.IcosahedronGeometry(2.2, 2);
    const icoMat = new THREE.MeshBasicMaterial({
      color: 0x00f0ff,
      wireframe: true,
      transparent: true,
      opacity: 0.75,
      blending: THREE.AdditiveBlending,
    });
    const icoMesh = new THREE.Mesh(icoGeo, icoMat);
    coreGroup.add(icoMesh);

    // Inner glowing sphere
    const sphereGeo = new THREE.SphereGeometry(1.5, 24, 24);
    const sphereMat = new THREE.MeshBasicMaterial({
      color: 0x0088cc,
      wireframe: true,
      transparent: true,
      opacity: 0.35,
      blending: THREE.AdditiveBlending,
    });
    const sphereMesh = new THREE.Mesh(sphereGeo, sphereMat);
    coreGroup.add(sphereMesh);

    // 4. Concentric Gyro HUD Rings
    const ringsGroup = new THREE.Group();
    scene.add(ringsGroup);

    function createRing(radius: number, segments: number, color: number, opacity: number, dashPattern = false) {
      const pts: THREE.Vector3[] = [];
      for (let i = 0; i <= segments; i++) {
        const theta = (i / segments) * Math.PI * 2;
        if (dashPattern && i % 4 >= 2) continue;
        pts.push(new THREE.Vector3(Math.cos(theta) * radius, Math.sin(theta) * radius, 0));
      }
      const geo = new THREE.BufferGeometry().setFromPoints(pts);
      const mat = new THREE.LineBasicMaterial({
        color,
        transparent: true,
        opacity,
        blending: THREE.AdditiveBlending,
        linewidth: 1,
      });
      return new THREE.Line(geo, mat);
    }

    const ring1 = createRing(3.4, 64, 0x00f0ff, 0.7, true);
    ring1.rotation.x = Math.PI / 6;
    ringsGroup.add(ring1);

    const ring2 = createRing(4.3, 80, 0x00c8ff, 0.5, false);
    ring2.rotation.y = -Math.PI / 5;
    ringsGroup.add(ring2);

    const ring3 = createRing(5.2, 96, 0xffa200, 0.65, true);
    ring3.rotation.x = -Math.PI / 4;
    ringsGroup.add(ring3);

    const ring4 = createRing(6.0, 120, 0x00f0ff, 0.4, false);
    ringsGroup.add(ring4);

    // Outer Reticle Ticks
    const tickGroup = new THREE.Group();
    const tickCount = 48;
    const tickRadius = 6.8;
    for (let i = 0; i < tickCount; i++) {
      const angle = (i / tickCount) * Math.PI * 2;
      const len = i % 4 === 0 ? 0.45 : 0.2;
      const x1 = Math.cos(angle) * tickRadius;
      const y1 = Math.sin(angle) * tickRadius;
      const x2 = Math.cos(angle) * (tickRadius + len);
      const y2 = Math.sin(angle) * (tickRadius + len);

      const tickGeo = new THREE.BufferGeometry().setFromPoints([
        new THREE.Vector3(x1, y1, 0),
        new THREE.Vector3(x2, y2, 0),
      ]);
      const tickMat = new THREE.LineBasicMaterial({
        color: i % 4 === 0 ? 0xffaa00 : 0x00f0ff,
        transparent: true,
        opacity: 0.6,
        blending: THREE.AdditiveBlending,
      });
      tickGroup.add(new THREE.Line(tickGeo, tickMat));
    }
    ringsGroup.add(tickGroup);

    // 5. Soundwave Reactive Particle Spectrum
    const particleCount = 140;
    const waveParticlesGeo = new THREE.BufferGeometry();
    const wavePos = new Float32Array(particleCount * 3);
    const baseWaveRadius = 4.7;

    for (let i = 0; i < particleCount; i++) {
      const angle = (i / particleCount) * Math.PI * 2;
      wavePos[i * 3] = Math.cos(angle) * baseWaveRadius;
      wavePos[i * 3 + 1] = Math.sin(angle) * baseWaveRadius;
      wavePos[i * 3 + 2] = 0;
    }

    waveParticlesGeo.setAttribute('position', new THREE.BufferAttribute(wavePos, 3));
    const waveMat = new THREE.PointsMaterial({
      color: 0x00f0ff,
      size: 0.16,
      transparent: true,
      opacity: 0.85,
      blending: THREE.AdditiveBlending,
    });
    const wavePoints = new THREE.Points(waveParticlesGeo, waveMat);
    scene.add(wavePoints);

    // 6. Floating Holographic Data Dust
    const dustCount = mode === 'embedded' ? 80 : 200;
    const dustGeo = new THREE.BufferGeometry();
    const dustPos = new Float32Array(dustCount * 3);
    for (let i = 0; i < dustCount * 3; i += 3) {
      dustPos[i] = (Math.random() - 0.5) * 35;
      dustPos[i + 1] = (Math.random() - 0.5) * 35;
      dustPos[i + 2] = (Math.random() - 0.5) * 20;
    }
    dustGeo.setAttribute('position', new THREE.BufferAttribute(dustPos, 3));
    const dustMat = new THREE.PointsMaterial({
      color: 0x00d0ff,
      size: 0.08,
      transparent: true,
      opacity: 0.4,
      blending: THREE.AdditiveBlending,
    });
    const dustPoints = new THREE.Points(dustGeo, dustMat);
    scene.add(dustPoints);

    // 7. Interactive 3D Neural Network Matrix (Synaptic Transformer Lattice)
    const neuralGroup = new THREE.Group();
    scene.add(neuralGroup);

    const nodeCount = mode === 'embedded' ? 24 : 44;
    const nodePositions: THREE.Vector3[] = [];

    for (let i = 0; i < nodeCount; i++) {
      let r = 2.4 + (i / nodeCount) * 4.8;
      let theta = (i / nodeCount) * Math.PI * 2 * 2.5 + (i % 3) * 0.9;
      let phi = Math.sin(i * 1.9) * 1.15;
      let pos = new THREE.Vector3(
        r * Math.cos(theta) * Math.cos(phi),
        r * Math.sin(phi),
        r * Math.sin(theta) * Math.cos(phi)
      );
      nodePositions.push(pos);
    }

    const nodeGeo = new THREE.BufferGeometry();
    const nodePositionsArr = new Float32Array(nodeCount * 3);
    for (let i = 0; i < nodeCount; i++) {
      nodePositionsArr[i * 3] = nodePositions[i].x;
      nodePositionsArr[i * 3 + 1] = nodePositions[i].y;
      nodePositionsArr[i * 3 + 2] = nodePositions[i].z;
    }
    nodeGeo.setAttribute('position', new THREE.BufferAttribute(nodePositionsArr, 3));
    const nodeMat = new THREE.PointsMaterial({
      color: 0x00f0ff,
      size: mode === 'embedded' ? 0.32 : 0.45,
      transparent: true,
      opacity: 0.9,
      blending: THREE.AdditiveBlending,
    });
    const neuralPoints = new THREE.Points(nodeGeo, nodeMat);
    neuralGroup.add(neuralPoints);

    // Glowing Synaptic Axon Connections
    const axonPts: THREE.Vector3[] = [];
    const axonPairs: [number, number][] = [];
    for (let i = 0; i < nodeCount; i++) {
      for (let j = i + 1; j < nodeCount; j++) {
        let dist = nodePositions[i].distanceTo(nodePositions[j]);
        if (dist > 1.0 && dist < 3.4) {
          axonPts.push(nodePositions[i], nodePositions[j]);
          axonPairs.push([i, j]);
        }
      }
    }
    const axonGeo = new THREE.BufferGeometry().setFromPoints(axonPts);
    const axonMat = new THREE.LineBasicMaterial({
      color: 0x0099dd,
      transparent: true,
      opacity: 0.28,
      blending: THREE.AdditiveBlending,
    });
    const axonLines = new THREE.LineSegments(axonGeo, axonMat);
    neuralGroup.add(axonLines);

    // Dynamic Action Potential Pulses (Signals traveling across axons)
    const pulseCount = mode === 'embedded' ? 12 : 26;
    const pulseGeo = new THREE.BufferGeometry();
    const pulsePosArr = new Float32Array(pulseCount * 3);
    const pulseProgress = new Float32Array(pulseCount);
    const pulsePairIdx = new Int32Array(pulseCount);

    for (let p = 0; p < pulseCount; p++) {
      pulsePairIdx[p] = Math.floor(Math.random() * Math.max(1, axonPairs.length));
      pulseProgress[p] = Math.random();
    }
    pulseGeo.setAttribute('position', new THREE.BufferAttribute(pulsePosArr, 3));
    const pulseMat = new THREE.PointsMaterial({
      color: 0x00ffff,
      size: 0.28,
      transparent: true,
      opacity: 0.95,
      blending: THREE.AdditiveBlending,
    });
    const pulsePoints = new THREE.Points(pulseGeo, pulseMat);
    neuralGroup.add(pulsePoints);

    // Mouse Parallax effect
    let mouseX = 0;
    let mouseY = 0;
    const handleMouseMove = (e: MouseEvent) => {
      mouseX = (e.clientX / window.innerWidth) * 2 - 1;
      mouseY = -(e.clientY / window.innerHeight) * 2 + 1;
    };
    window.addEventListener('mousemove', handleMouseMove);

    // Resize Observer for dynamic layout
    const resizeObserver = new ResizeObserver(() => {
      if (!container) return;
      const w = container.clientWidth;
      const h = container.clientHeight;
      if (w > 0 && h > 0) {
        camera.aspect = w / h;
        camera.updateProjectionMatrix();
        renderer.setSize(w, h);
      }
    });
    resizeObserver.observe(container);

    // 8. Animation Loop
    let clock = new THREE.Clock();
    let animId: number;

    const animate = () => {
      animId = requestAnimationFrame(animate);
      const elapsed = clock.getElapsedTime();
      const currentState = stateRef.current;

      // Parallax camera lerp
      camera.position.x += (mouseX * (mode === 'embedded' ? 1.2 : 2.5) - camera.position.x) * 0.05;
      camera.position.y += (mouseY * (mode === 'embedded' ? 1.2 : 2.5) - camera.position.y) * 0.05;
      camera.lookAt(0, 0, 0);

      // Core rotation & pulse speed
      let speedMult = 1.0;
      let waveAmpl = 0.2;
      let coreColor = 0x00f0ff;

      if (currentState === 'thinking') {
        speedMult = 3.2;
        waveAmpl = 0.5;
        coreColor = 0xffa200; // Stark Amber when computing
      } else if (currentState === 'speaking') {
        speedMult = 2.0;
        waveAmpl = 0.9;
        coreColor = 0x00ffff; // Electric cyan
      } else if (currentState === 'listening') {
        speedMult = 1.4;
        waveAmpl = 0.4;
        coreColor = 0x38ef7d; // Greenish-cyan for active mic
      }

      icoMat.color.setHex(coreColor);
      coreLight.color.setHex(coreColor);

      // Rotate rings at harmonic rates
      coreGroup.rotation.y = elapsed * 0.6 * speedMult;
      coreGroup.rotation.x = elapsed * 0.4 * speedMult;

      ring1.rotation.z = elapsed * 0.8 * speedMult;
      ring2.rotation.z = -elapsed * 0.6 * speedMult;
      ring3.rotation.z = elapsed * 0.35 * speedMult;
      ring4.rotation.z = -elapsed * 0.2 * speedMult;
      tickGroup.rotation.z = elapsed * 0.15 * speedMult;

      // Breathing scale on core
      const pulse = 1 + Math.sin(elapsed * 3 * speedMult) * (currentState === 'speaking' ? 0.15 : 0.06);
      coreGroup.scale.set(pulse, pulse, pulse);

      // Animate Soundwave Particle Spectrum
      const positions = waveParticlesGeo.attributes.position.array as Float32Array;
      for (let i = 0; i < particleCount; i++) {
        const angle = (i / particleCount) * Math.PI * 2;
        const wave =
          Math.sin(angle * 7 + elapsed * 5 * speedMult) *
          Math.cos(angle * 3 - elapsed * 3) *
          waveAmpl;
        const r = baseWaveRadius + wave;
        positions[i * 3] = Math.cos(angle) * r;
        positions[i * 3 + 1] = Math.sin(angle) * r;
        positions[i * 3 + 2] = Math.sin(angle * 4 + elapsed * 2) * 0.4;
      }
      waveParticlesGeo.attributes.position.needsUpdate = true;

      // Animate Neural Action Potentials across synapses
      let pulseSpeed = 1.0;
      if (currentState === 'thinking') {
        pulseSpeed = 3.4;
        nodeMat.color.setHex(0xffa200);
        axonMat.color.setHex(0xff8800);
        pulseMat.color.setHex(0xffffff);
        axonMat.opacity = 0.6;
      } else if (currentState === 'speaking') {
        pulseSpeed = 1.9;
        nodeMat.color.setHex(0x00ffff);
        axonMat.color.setHex(0x00b0ff);
        pulseMat.color.setHex(0x00f0ff);
        axonMat.opacity = 0.48;
      } else if (currentState === 'listening') {
        pulseSpeed = 1.3;
        nodeMat.color.setHex(0x38ef7d);
        axonMat.color.setHex(0x20c997);
        pulseMat.color.setHex(0x70ffaa);
        axonMat.opacity = 0.38;
      } else {
        nodeMat.color.setHex(0x00d0ff);
        axonMat.color.setHex(0x0077aa);
        pulseMat.color.setHex(0x00ffff);
        axonMat.opacity = 0.22;
      }

      if (axonPairs.length > 0) {
        const pArr = pulseGeo.attributes.position.array as Float32Array;
        for (let p = 0; p < pulseCount; p++) {
          pulseProgress[p] += 0.016 * pulseSpeed;
          if (pulseProgress[p] >= 1.0) {
            pulseProgress[p] = 0;
            pulsePairIdx[p] = Math.floor(Math.random() * axonPairs.length);
          }
          const pair = axonPairs[pulsePairIdx[p] % axonPairs.length];
          const from = nodePositions[pair[0]];
          const to = nodePositions[pair[1]];
          const t = pulseProgress[p];
          pArr[p * 3] = from.x + (to.x - from.x) * t;
          pArr[p * 3 + 1] = from.y + (to.y - from.y) * t;
          pArr[p * 3 + 2] = from.z + (to.z - from.z) * t;
        }
        pulseGeo.attributes.position.needsUpdate = true;
      }

      // Rotate neural group harmonically
      neuralGroup.rotation.y = elapsed * 0.14 * speedMult;
      neuralGroup.rotation.x = Math.sin(elapsed * 0.22) * 0.12;

      dustPoints.rotation.y = elapsed * 0.03;

      renderer.render(scene, camera);
    };

    animate();

    return () => {
      cancelAnimationFrame(animId);
      window.removeEventListener('mousemove', handleMouseMove);
      resizeObserver.disconnect();
      renderer.dispose();
      icoGeo.dispose();
      icoMat.dispose();
      sphereGeo.dispose();
      sphereMat.dispose();
      waveParticlesGeo.dispose();
      waveMat.dispose();
      dustGeo.dispose();
      dustMat.dispose();
      nodeGeo.dispose();
      nodeMat.dispose();
      axonGeo.dispose();
      axonMat.dispose();
      pulseGeo.dispose();
      pulseMat.dispose();
      if (renderer.domElement.parentNode) {
        renderer.domElement.parentNode.removeChild(renderer.domElement);
      }
    };
  }, [mode]);

  const getStatusText = () => {
    switch (state) {
      case 'listening':
        return lang === 'ta-IN' ? 'குரல் கேட்கிறது… (LISTENING)' : 'AUDIO INGESTION ACTIVE · LISTENING…';
      case 'thinking':
        return lang === 'ta-IN' ? 'சிந்திக்கிறது… (COMPUTING)' : 'NEURAL CORE PROCESSING · CALCULATING…';
      case 'speaking':
        return lang === 'ta-IN' ? 'ஜார்விஸ் பேசுகிறார்… (SPEAKING)' : 'VOCAL SYNTHESIS ACTIVE · RESPONDING…';
      default:
        return lang === 'ta-IN' ? 'ஜார்விஸ் தயார் · பேச அல்லது தட்டச்சு செய்ய தொடங்குங்கள்' : 'J.A.R.V.I.S. ONLINE · SYSTEMS NOMINAL';
    }
  };

  function handleFormSubmit(e: React.FormEvent) {
    e.preventDefault();
    if (!textDraft.trim() || state === 'thinking') return;
    onSendText?.(textDraft.trim());
    setTextDraft('');
  }

  // Embedded widget mode for Chat Workspace
  if (mode === 'embedded') {
    return (
      <div
        className="jarvis-hologram-embedded-container"
        onClick={onExpand}
        style={{ cursor: onExpand ? 'pointer' : 'default' }}
        title={onExpand ? 'Click to expand Iron Man J.A.R.V.I.S. 3D HUD' : undefined}
      >
        <div className="embedded-viewport" ref={mountRef} />
        <div className="embedded-hud-badge">
          <span className={`hud-status-dot ${state}`} />
          <span>{getStatusText()}</span>
          {onExpand && <span className="hud-expand-hint">[EXPAND ↗]</span>}
        </div>
      </div>
    );
  }

  // Full Cinematic Iron Man 3D HUD Mode
  return (
    <div className="jarvis-hologram-overlay" role="dialog" aria-modal="true" aria-label="JARVIS Holographic Mode">
      {/* Three.js 3D Viewport */}
      <div className="hologram-viewport" ref={mountRef} />

      {/* Futuristic Scanline and Vignette effects */}
      <div className="hud-scanlines" />
      <div className="hud-vignette" />

      {/* Top Left: System Mark & Identity */}
      <div className="hud-bracket hud-top-left">
        <div className="hud-tag">STARK INDUSTRIES // MARK VII</div>
        <div className="hud-title">J.A.R.V.I.S. HOLOGRAPHIC HUD</div>
        <div className="hud-sub">SEC_LEVEL: ALPHA · LOCAL ORBIT</div>
        <div className="hud-sub" style={{ color: '#00ffaa', marginTop: '4px', display: 'flex', alignItems: 'center', gap: '4px', fontSize: '0.74rem' }}>
          <span>🟢</span> <strong>BIOMETRICS: MASTER KABIL (VERIFIED)</strong>
        </div>
      </div>

      {/* Left Panel: Voice Commands & Communication Matrix Feed */}
      <div className="hud-bracket hud-left-feed">
        <div className="hud-feed-header">
          <div className="hud-tag">VOICE &amp; COMMAND FEED</div>
          <div className="hud-transcript-lang">
            {lang === 'ta-IN' ? 'தமிழ் (TAMIL)' : 'ENGLISH (US)'}
          </div>
        </div>

        <div className="hud-feed-content">
          {userTranscript || jarvisReply ? (
            <>
              {userTranscript && (
                <div className="hud-feed-item user">
                  <div className="hud-feed-badge">
                    <Mic size={12} />
                    <span>COMMAND // YOU</span>
                  </div>
                  <div className="hud-feed-text">{userTranscript}</div>
                </div>
              )}
              {jarvisReply && (
                <div className="hud-feed-item jarvis">
                  <div className="hud-feed-badge">
                    <Sparkles size={12} />
                    <span>J.A.R.V.I.S. // RESPONSE</span>
                  </div>
                  <div className="hud-feed-text">{jarvisReply}</div>
                </div>
              )}
            </>
          ) : (
            <div className="hud-feed-standby">
              <div className="hud-standby-radar">
                <span className="hud-radar-sweep" />
              </div>
              <div className="hud-standby-title">AUDIO SENSORS ACTIVE</div>
              <div className="hud-standby-sub">
                {lang === 'ta-IN'
                  ? 'குரல் அல்லது தட்டச்சு கட்டளைக்காக காத்திருக்கிறது…'
                  : 'Awaiting voice command or text instruction…'}
              </div>
            </div>
          )}
        </div>
      </div>

      {/* Top Right: Live Telemetry */}
      <div className="hud-bracket hud-top-right">
        <div className="hud-tag">HARDWARE TELEMETRY</div>
        <div className="hud-telemetry-row">
          <span>GPU:</span> <strong>{telemetry?.gpu || 'RTX 2050 (3.65 GB FREE)'}</strong>
        </div>
        <div className="hud-telemetry-row">
          <span>RAM:</span> <strong>{telemetry?.ram || '5.86 GB (0.62 GB FREE)'}</strong>
        </div>
        <div className="hud-telemetry-row">
          <span>ROM:</span> <strong>{telemetry?.rom || '237.4 GB (21.1 GB FREE)'}</strong>
        </div>
        <div className="hud-telemetry-row">
          <span>AI CORE:</span> <strong>{telemetry?.model || 'OLLAMA LOCAL'}</strong>
        </div>
      </div>

      {/* Center Status Glow Badge */}
      <div className="hud-center-status">
        <div className={`hud-status-badge ${state}`}>
          <span className="hud-status-dot" />
          <span>{getStatusText()}</span>
        </div>
      </div>

      {/* Lower Console: Dedicated Controls (Clean text input without overlapping background cards) */}
      <div className="hud-lower-console">
        {/* Mode Selector Tabs: Clear separation of Voice & Text */}
        <div className="hud-mode-tabs" role="tablist">
          <button
            type="button"
            role="tab"
            aria-selected={activeTab === 'voice'}
            className={`hud-tab-btn ${activeTab === 'voice' ? 'active' : ''}`}
            onClick={() => setActiveTab('voice')}
          >
            <Mic size={14} />
            <span>VOICE INTERFACE</span>
          </button>
          <button
            type="button"
            role="tab"
            aria-selected={activeTab === 'text'}
            className={`hud-tab-btn ${activeTab === 'text' ? 'active' : ''}`}
            onClick={() => setActiveTab('text')}
          >
            <Send size={13} />
            <span>TEXT CONSOLE</span>
          </button>
        </div>

        {/* Dedicated Voice Deck */}
        {activeTab === 'voice' && (
          <div className="hud-voice-deck">
            <button
              type="button"
              className={`hud-mic-button ${state === 'listening' ? 'active' : ''}`}
              onClick={onMicClick}
              title={state === 'listening' ? 'Stop listening' : 'Start speaking'}
            >
              <span className="hud-mic-ring" />
              <span className="hud-mic-icon">
                {state === 'listening' ? <Square size={16} /> : <Mic size={20} />}
              </span>
              <span>{state === 'listening' ? 'HALT' : 'VOICE'}</span>
            </button>

            <div className="hud-deck-actions">
              {onLangChange && (
                <div className="hud-lang-toggle">
                  <button
                    type="button"
                    className={`hud-chip ${lang === 'en-US' ? 'selected' : ''}`}
                    onClick={() => onLangChange('en-US')}
                  >
                    EN (US)
                  </button>
                  <button
                    type="button"
                    className={`hud-chip ${lang === 'ta-IN' ? 'selected' : ''}`}
                    onClick={() => onLangChange('ta-IN')}
                  >
                    தமிழ் (TAMIL)
                  </button>
                </div>
              )}

              {onVoiceRateChange && (
                <div className="hud-speed-toggle" style={{ display: 'inline-flex', alignItems: 'center', gap: '4px' }}>
                  <span style={{ fontSize: '0.66rem', color: '#38bdf8', letterSpacing: '0.5px' }}>SPEED:</span>
                  {[0.8, 1.0, 1.25, 1.5].map((preset) => (
                    <button
                      key={preset}
                      type="button"
                      className={`hud-chip ${Math.abs(voiceRate - preset) < 0.03 ? 'selected' : ''}`}
                      style={{ padding: '2px 8px', fontSize: '0.72rem' }}
                      onClick={() => onVoiceRateChange(preset)}
                      title={`Set speech speed to ${preset}x`}
                    >
                      {preset}x
                    </button>
                  ))}
                </div>
              )}

              {onContinuousChange && (
                <label className="hud-checkbox-label">
                  <input
                    type="checkbox"
                    checked={isContinuous}
                    onChange={(e) => onContinuousChange(e.target.checked)}
                  />
                  <span>CONTINUOUS SPEECH</span>
                </label>
              )}

              {onClose && (
                <button type="button" className="hud-close-button" onClick={onClose}>
                  EXIT HUD [ESC]
                </button>
              )}
            </div>
          </div>
        )}

        {/* Dedicated Text Deck */}
        {activeTab === 'text' && (
          <div className="hud-text-deck">
            <form className="hud-text-composer" onSubmit={handleFormSubmit}>
              <input
                type="text"
                value={textDraft}
                onChange={(e) => setTextDraft(e.target.value)}
                placeholder={
                  lang === 'ta-IN'
                    ? 'ஜார்விஸிடம் கேளுங்க பாஸ்… (e.g. குரோம் ஓபன் பண்ணு, மணி என்ன)'
                    : 'Transmit command to J.A.R.V.I.S.… (e.g. open chrome, open notepad, date)'
                }
                disabled={state === 'thinking'}
                autoFocus
              />
              <button
                type="button"
                className="hud-dictate-btn"
                onClick={onMicClick}
                title="Dictate with voice"
              >
                <Mic size={14} />
              </button>
              <button
                type="submit"
                disabled={!textDraft.trim() || state === 'thinking'}
                title="Transmit command"
              >
                <Send size={14} />
                <span>TRANSMIT</span>
              </button>
            </form>

            <div className="hud-deck-actions">
              {onLangChange && (
                <div className="hud-lang-toggle">
                  <button
                    type="button"
                    className={`hud-chip ${lang === 'en-US' ? 'selected' : ''}`}
                    onClick={() => onLangChange('en-US')}
                  >
                    EN (US)
                  </button>
                  <button
                    type="button"
                    className={`hud-chip ${lang === 'ta-IN' ? 'selected' : ''}`}
                    onClick={() => onLangChange('ta-IN')}
                  >
                    தமிழ் (TAMIL)
                  </button>
                </div>
              )}

              {onVoiceRateChange && (
                <div className="hud-speed-toggle" style={{ display: 'inline-flex', alignItems: 'center', gap: '4px' }}>
                  <span style={{ fontSize: '0.66rem', color: '#38bdf8', letterSpacing: '0.5px' }}>SPEED:</span>
                  {[0.8, 1.0, 1.25, 1.5].map((preset) => (
                    <button
                      key={preset}
                      type="button"
                      className={`hud-chip ${Math.abs(voiceRate - preset) < 0.03 ? 'selected' : ''}`}
                      style={{ padding: '2px 8px', fontSize: '0.72rem' }}
                      onClick={() => onVoiceRateChange(preset)}
                      title={`Set speech speed to ${preset}x`}
                    >
                      {preset}x
                    </button>
                  ))}
                </div>
              )}

              {onClose && (
                <button type="button" className="hud-close-button" onClick={onClose}>
                  EXIT HUD [ESC]
                </button>
              )}
            </div>
          </div>
        )}
      </div>
    </div>
  );
});

export default JarvisHologram;
