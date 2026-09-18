import {
  AfterViewInit,
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  OnDestroy,
  ViewChild,
  inject,
} from '@angular/core';

// Alternate deep and luminous tones so each folded wave remains distinct.
const LIGHT_PALETTE = ['#06483f', '#31bf98', '#086354', '#26a791', '#103e50'];
const DARK_PALETTE = ['#041e24', '#238e78', '#073b34', '#167e72', '#071f30'];

@Component({
  selector: 'app-gradient-wave',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: '<canvas #canvas aria-hidden="true"></canvas>',
  styles: `
    :host {
      position: absolute;
      inset: 0;
      display: block;
      overflow: hidden;
      background:
        radial-gradient(ellipse at 110% 15%, #103e50 25%, transparent 26%),
        radial-gradient(ellipse at 0% 35%, #26a791 30%, transparent 31%),
        radial-gradient(ellipse at 100% 65%, #086354 35%, transparent 36%),
        linear-gradient(145deg, #06483f, #31bf98);
      pointer-events: none;
    }

    canvas {
      display: block;
      width: 100%;
      height: 100%;
    }

    :host-context(html[data-theme='dark']) {
      background:
        radial-gradient(ellipse at 110% 15%, #071f30 25%, transparent 26%),
        radial-gradient(ellipse at 0% 35%, #167e72 30%, transparent 31%),
        radial-gradient(ellipse at 100% 65%, #073b34 35%, transparent 36%),
        linear-gradient(145deg, #041e24, #238e78);
    }
  `,
})
export class GradientWave implements AfterViewInit, OnDestroy {
  private readonly host = inject(ElementRef<HTMLElement>);

  @ViewChild('canvas', { static: true })
  private canvasRef!: ElementRef<HTMLCanvasElement>;

  private gl: WebGLRenderingContext | null = null;
  private program: WebGLProgram | null = null;
  private positionBuffer: WebGLBuffer | null = null;
  private resolutionLocation: WebGLUniformLocation | null = null;
  private timeLocation: WebGLUniformLocation | null = null;
  private colorsLocation: WebGLUniformLocation | null = null;
  private animationFrame?: number;
  private resizeObserver?: ResizeObserver;
  private themeObserver?: MutationObserver;
  private reducedMotion?: MediaQueryList;
  private startedAt = 0;

  ngAfterViewInit(): void {
    if (typeof WebGLRenderingContext === 'undefined') {
      return;
    }

    if (!this.initializeWebGl()) {
      return;
    }

    this.resizeObserver = new ResizeObserver(() => this.resize());
    this.resizeObserver.observe(this.host.nativeElement);

    this.themeObserver = new MutationObserver(() => this.updatePalette());
    this.themeObserver.observe(document.documentElement, {
      attributeFilter: ['data-theme'],
    });

    this.reducedMotion = window.matchMedia('(prefers-reduced-motion: reduce)');
    this.reducedMotion.addEventListener('change', this.handleMotionPreference);

    this.startedAt = performance.now();
    this.resize();
    this.updatePalette();
    this.render(this.startedAt);
  }

  ngOnDestroy(): void {
    if (this.animationFrame !== undefined) {
      cancelAnimationFrame(this.animationFrame);
    }

    this.resizeObserver?.disconnect();
    this.themeObserver?.disconnect();
    this.reducedMotion?.removeEventListener('change', this.handleMotionPreference);

    if (this.gl) {
      if (this.positionBuffer) {
        this.gl.deleteBuffer(this.positionBuffer);
      }
      if (this.program) {
        this.gl.deleteProgram(this.program);
      }
    }
  }

  private initializeWebGl(): boolean {
    const canvas = this.canvasRef.nativeElement;
    const gl = canvas.getContext('webgl', {
      alpha: false,
      antialias: true,
      powerPreference: 'low-power',
    });

    if (!gl) {
      return false;
    }

    const vertexShader = this.compileShader(
      gl,
      gl.VERTEX_SHADER,
      `
        attribute vec2 a_position;

        void main() {
          gl_Position = vec4(a_position, 0.0, 1.0);
        }
      `,
    );
    const fragmentShader = this.compileShader(
      gl,
      gl.FRAGMENT_SHADER,
      `
        precision highp float;

        uniform vec2 u_resolution;
        uniform float u_time;
        uniform vec3 u_colors[5];

        float hash(vec2 point) {
          return fract(sin(dot(point, vec2(127.1, 311.7))) * 43758.5453123);
        }

        float noise(vec2 point) {
          vec2 cell = floor(point);
          vec2 local = fract(point);
          vec2 curve = local * local * (3.0 - 2.0 * local);

          float a = hash(cell);
          float b = hash(cell + vec2(1.0, 0.0));
          float c = hash(cell + vec2(0.0, 1.0));
          float d = hash(cell + vec2(1.0, 1.0));

          return mix(mix(a, b, curve.x), mix(c, d, curve.x), curve.y);
        }

        float fbm(vec2 point) {
          float value = 0.0;
          float amplitude = 0.52;
          mat2 rotation = mat2(0.82, -0.57, 0.57, 0.82);

          for (int i = 0; i < 4; i++) {
            value += amplitude * noise(point);
            point = rotation * point * 2.02 + 8.7;
            amplitude *= 0.5;
          }

          return value;
        }

        vec3 waveLayer(vec3 underneath, vec3 tint, float y, float boundary) {
          float edge = y - boundary;
          // Defined folds with enough feathering to avoid visible seams.
          float mask = smoothstep(-0.05, 0.05, edge);
          float crest = 1.0 - smoothstep(0.0, 0.085, abs(edge - 0.018));
          float shadow = 1.0 - smoothstep(0.0, 0.095, abs(edge + 0.028));
          vec3 shaded = tint * (0.82 + 0.18 * smoothstep(-0.04, 0.22, edge));
          vec3 color = mix(underneath * (1.0 - shadow * 0.2), shaded, mask);
          return color + tint * crest * 0.14;
        }

        void main() {
          vec2 uv = gl_FragCoord.xy / max(u_resolution.xy, vec2(1.0));
          float aspect = u_resolution.x / max(u_resolution.y, 1.0);
          float time = u_time * 0.22;
          vec2 flowUv = vec2(uv.x * aspect, uv.y);

          float organicNoise = fbm(flowUv * 2.0 + vec2(time * 0.22, -time * 0.12));
          float bend = sin(uv.x * 5.2 + time) * 0.095;
          bend += sin(uv.x * 2.7 - time * 0.65) * 0.048;
          bend += (organicNoise - 0.5) * 0.055;
          float diagonal = (uv.x - 0.5) * 0.18;

          float waveOne = 0.16 + diagonal + bend;
          float waveTwo = 0.38 + diagonal + bend * 0.8 + sin(uv.x * 3.8 + time * 0.7 + 1.2) * 0.035;
          float waveThree = 0.61 + diagonal + bend * 0.9 + sin(uv.x * 4.0 - time * 0.5 + 2.4) * 0.035;
          float waveFour = 0.84 + diagonal + bend * 0.75;

          vec3 color = u_colors[0];
          color = waveLayer(color, u_colors[1], uv.y, waveOne);
          color = waveLayer(color, u_colors[2], uv.y, waveTwo);
          color = waveLayer(color, u_colors[3], uv.y, waveThree);
          color = waveLayer(color, u_colors[4], uv.y, waveFour);

          gl_FragColor = vec4(color, 1.0);
        }
      `,
    );

    if (!vertexShader || !fragmentShader) {
      return false;
    }

    const program = gl.createProgram();
    if (!program) {
      return false;
    }

    gl.attachShader(program, vertexShader);
    gl.attachShader(program, fragmentShader);
    gl.linkProgram(program);
    gl.deleteShader(vertexShader);
    gl.deleteShader(fragmentShader);

    if (!gl.getProgramParameter(program, gl.LINK_STATUS)) {
      gl.deleteProgram(program);
      return false;
    }

    const positionBuffer = gl.createBuffer();
    if (!positionBuffer) {
      gl.deleteProgram(program);
      return false;
    }

    gl.bindBuffer(gl.ARRAY_BUFFER, positionBuffer);
    gl.bufferData(gl.ARRAY_BUFFER, new Float32Array([-1, -1, 1, -1, -1, 1, 1, 1]), gl.STATIC_DRAW);

    gl.useProgram(program);
    const positionLocation = gl.getAttribLocation(program, 'a_position');
    gl.enableVertexAttribArray(positionLocation);
    gl.vertexAttribPointer(positionLocation, 2, gl.FLOAT, false, 0, 0);

    this.gl = gl;
    this.program = program;
    this.positionBuffer = positionBuffer;
    this.resolutionLocation = gl.getUniformLocation(program, 'u_resolution');
    this.timeLocation = gl.getUniformLocation(program, 'u_time');
    this.colorsLocation = gl.getUniformLocation(program, 'u_colors[0]');
    return true;
  }

  private compileShader(
    gl: WebGLRenderingContext,
    type: number,
    source: string,
  ): WebGLShader | null {
    const shader = gl.createShader(type);
    if (!shader) {
      return null;
    }

    gl.shaderSource(shader, source);
    gl.compileShader(shader);

    if (!gl.getShaderParameter(shader, gl.COMPILE_STATUS)) {
      gl.deleteShader(shader);
      return null;
    }

    return shader;
  }

  private resize(): void {
    if (!this.gl) {
      return;
    }

    const canvas = this.canvasRef.nativeElement;
    const bounds = this.host.nativeElement.getBoundingClientRect();
    const pixelRatio = Math.min(window.devicePixelRatio || 1, 1.5);
    const width = Math.max(1, Math.round(bounds.width * pixelRatio));
    const height = Math.max(1, Math.round(bounds.height * pixelRatio));

    if (canvas.width !== width || canvas.height !== height) {
      canvas.width = width;
      canvas.height = height;
      this.gl.viewport(0, 0, width, height);
      this.gl.uniform2f(this.resolutionLocation, width, height);
      this.draw(this.reducedMotion?.matches ? 0 : (performance.now() - this.startedAt) / 1000);
    }
  }

  private updatePalette(): void {
    if (!this.gl || !this.program || !this.colorsLocation) {
      return;
    }

    const palette =
      document.documentElement.dataset['theme'] === 'dark' ? DARK_PALETTE : LIGHT_PALETTE;
    const normalized = palette.flatMap((color) => this.normalizeColor(color));

    this.gl.useProgram(this.program);
    this.gl.uniform3fv(this.colorsLocation, new Float32Array(normalized));
    this.draw(this.reducedMotion?.matches ? 0 : (performance.now() - this.startedAt) / 1000);
  }

  private normalizeColor(color: string): [number, number, number] {
    const value = Number.parseInt(color.slice(1), 16);
    return [((value >> 16) & 255) / 255, ((value >> 8) & 255) / 255, (value & 255) / 255];
  }

  private readonly render = (timestamp: number): void => {
    const elapsed = (timestamp - this.startedAt) / 1000;
    this.draw(this.reducedMotion?.matches ? 0 : elapsed);

    if (!this.reducedMotion?.matches) {
      this.animationFrame = requestAnimationFrame(this.render);
    }
  };

  private draw(time: number): void {
    if (!this.gl || !this.program) {
      return;
    }

    this.gl.useProgram(this.program);
    this.gl.uniform1f(this.timeLocation, time);
    this.gl.drawArrays(this.gl.TRIANGLE_STRIP, 0, 4);
  }

  private readonly handleMotionPreference = (): void => {
    if (this.animationFrame !== undefined) {
      cancelAnimationFrame(this.animationFrame);
      this.animationFrame = undefined;
    }

    if (this.reducedMotion?.matches) {
      this.draw(0);
      return;
    }

    this.startedAt = performance.now();
    this.animationFrame = requestAnimationFrame(this.render);
  };
}
