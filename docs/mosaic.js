(function(){
  "use strict";
  var canvas = document.getElementById('mosaic');
  var ctx = canvas.getContext('2d');
  var hero = document.getElementById('hero');
  var hint = document.getElementById('hint');
  var reduced = window.matchMedia('(prefers-reduced-motion: reduce)').matches;

  // palette: cobalt, deep cobalt, turquoise, deep turquoise, amber, rose, indigo tile
  var COLORS = [
    [46,82,224],[35,60,168],[46,196,182],[24,132,124],
    [242,169,59],[232,93,138],[27,31,58]
  ];
  var CERAMIC = [239,234,224];

  var W=0, H=0, cell=56, cols=0, rows=0, shards=[];

  function field(x,y,t){
    var v = 0.5
      + 0.28*Math.sin(x*0.0042 + t*0.35)
      + 0.22*Math.sin(y*0.0051 - t*0.22 + x*0.0017)
      + 0.12*Math.sin((x+y)*0.003 + t*0.15);
    return Math.min(0.999, Math.max(0, v));
  }
  function pickColor(s,t){
    if (s.r0 < 0.15) return CERAMIC.slice();
    var v = field(s.cx, s.cy, t);
    var c = COLORS[Math.floor(v*COLORS.length)];
    var sh = s.shade;
    return [c[0]*sh, c[1]*sh, c[2]*sh];
  }

  function build(){
    var dpr = Math.min(window.devicePixelRatio||1, 2);
    W = hero.clientWidth; H = hero.clientHeight;
    canvas.width = Math.round(W*dpr); canvas.height = Math.round(H*dpr);
    ctx.setTransform(dpr,0,0,dpr,0,0);
    cell = Math.max(42, Math.min(64, W/20));
    cols = Math.ceil(W/cell)+2;
    rows = Math.ceil(H/cell)+2;
    // jittered grid points, origin shifted half a cell off-canvas
    var pts = [];
    for (var r=0; r<=rows; r++){
      pts.push([]);
      for (var c=0; c<=cols; c++){
        pts[r].push([
          (c-0.5)*cell + (Math.random()-0.5)*cell*0.62,
          (r-0.5)*cell + (Math.random()-0.5)*cell*0.62
        ]);
      }
    }
    shards = [];
    for (r=0; r<rows; r++){
      for (c=0; c<cols; c++){
        var q = [pts[r][c], pts[r][c+1], pts[r+1][c+1], pts[r+1][c]];
        var cx=0, cy=0, i;
        for (i=0;i<4;i++){ cx+=q[i][0]/4; cy+=q[i][1]/4; }
        var poly = [];
        for (i=0;i<4;i++){
          poly.push([ cx + (q[i][0]-cx)*0.90, cy + (q[i][1]-cy)*0.90 ]);
        }
        var s = { poly:poly, cx:cx, cy:cy, r0:Math.random(),
                  shade:0.88+Math.random()*0.24, flash:0, col:null, tgt:null };
        s.col = pickColor(s, 0);
        s.tgt = s.col.slice();
        shards.push(s);
      }
    }
    draw();
  }

  function draw(){
    ctx.fillStyle = '#141830'; // mortar
    ctx.fillRect(0,0,W,H);
    for (var i=0;i<shards.length;i++){
      var s = shards[i], p = s.poly;
      ctx.beginPath();
      ctx.moveTo(p[0][0],p[0][1]);
      ctx.lineTo(p[1][0],p[1][1]);
      ctx.lineTo(p[2][0],p[2][1]);
      ctx.lineTo(p[3][0],p[3][1]);
      ctx.closePath();
      ctx.fillStyle = 'rgb('+(s.col[0]|0)+','+(s.col[1]|0)+','+(s.col[2]|0)+')';
      ctx.fill();
      if (s.flash > 0.02){
        ctx.strokeStyle = 'rgba(239,234,224,'+s.flash.toFixed(2)+')';
        ctx.lineWidth = 2.5;
        ctx.stroke();
      }
    }
  }

  var T = 0, last = 0;
  function frame(ts){
    requestAnimationFrame(frame);
    if (ts - last < 33) return;
    var dt = Math.min(0.1, (ts-last)/1000);
    last = ts; T += dt;
    if (!reduced){
      for (var k=0;k<3;k++){
        var s = shards[(Math.random()*shards.length)|0];
        s.tgt = pickColor(s, T);
      }
    }
    var ease = 1 - Math.exp(-dt*2.2);
    for (var i=0;i<shards.length;i++){
      var sh = shards[i];
      sh.col[0] += (sh.tgt[0]-sh.col[0])*ease;
      sh.col[1] += (sh.tgt[1]-sh.col[1])*ease;
      sh.col[2] += (sh.tgt[2]-sh.col[2])*ease;
      if (sh.flash > 0) sh.flash = Math.max(0, sh.flash - dt*1.8);
    }
    draw();
  }

  /* ---------- audio: hue -> pitch, Spanish Gipsy scale, green = 440 Hz ---------- */
  var ac=null, master=null;
  var SCALE = [0,1,4,5,7,8,10];
  function ensureAudio(){
    if (ac) return;
    var AC = window.AudioContext || window.webkitAudioContext;
    if (!AC) return;
    ac = new AC();
    master = ac.createGain();
    master.gain.value = 0.55;
    master.connect(ac.destination);
    var dly = ac.createDelay(1.0); dly.delayTime.value = 0.31;
    var fb = ac.createGain(); fb.gain.value = 0.34;
    var wet = ac.createGain(); wet.gain.value = 0.22;
    master.connect(dly); dly.connect(fb); fb.connect(dly);
    dly.connect(wet); wet.connect(ac.destination);
  }
  function hueOf(c){
    var r=c[0]/255, g=c[1]/255, b=c[2]/255;
    var mx=Math.max(r,g,b), mn=Math.min(r,g,b), d=mx-mn;
    if (d < 0.001) return 120; // neutrals sing green
    var h;
    if (mx===r) h = ((g-b)/d) % 6;
    else if (mx===g) h = (b-r)/d + 2;
    else h = (r-g)/d + 4;
    return ((h*60)+360) % 360;
  }
  function playShard(s, px){
    ensureAudio();
    if (!ac) return;
    if (ac.state === 'suspended') ac.resume();
    var hue = hueOf(s.col);
    var lum = (0.2126*s.col[0] + 0.7152*s.col[1] + 0.0722*s.col[2]) / 255;
    // 14 scale degrees over two octaves; hue 120 (green) lands on degree 7 = A440
    var idx = (Math.floor(((hue - 120 + 360) % 360) / 360 * 14) + 7) % 14;
    var semis = SCALE[idx % 7] + 12*Math.floor(idx/7);
    var freq = 220 * Math.pow(2, semis/12);
    var t0 = ac.currentTime;
    var osc = ac.createOscillator();
    osc.type = 'triangle';
    osc.frequency.value = freq;
    var flt = ac.createBiquadFilter();
    flt.type = 'lowpass';
    flt.frequency.value = 500 + lum*2800;
    flt.Q.value = 5;
    var g = ac.createGain();
    g.gain.setValueAtTime(0.0001, t0);
    g.gain.exponentialRampToValueAtTime(0.10 + lum*0.16, t0+0.012);
    g.gain.exponentialRampToValueAtTime(0.0001, t0+0.55);
    var out = g;
    if (ac.createStereoPanner){
      var pan = ac.createStereoPanner();
      pan.pan.value = Math.max(-1, Math.min(1, (px/W)*2 - 1));
      g.connect(pan); out = pan;
    }
    osc.connect(flt); flt.connect(g); out.connect(master);
    osc.start(t0); osc.stop(t0+0.6);
    s.flash = 1;
  }

  function shardAt(x,y){
    var c = Math.min(cols-1, Math.max(0, Math.floor(x/cell + 0.5)));
    var r = Math.min(rows-1, Math.max(0, Math.floor(y/cell + 0.5)));
    return shards[r*cols + c] || null;
  }

  var lastShard = null, lastNote = 0, played = false;
  function trigger(e){
    var rect = canvas.getBoundingClientRect();
    var x = e.clientX - rect.left, y = e.clientY - rect.top;
    var s = shardAt(x,y);
    if (!s) return;
    var now = performance.now();
    if (s === lastShard && now - lastNote < 220) return;
    if (now - lastNote < 70) return;
    lastShard = s; lastNote = now;
    playShard(s, x);
    if (!played){ played = true; hint.classList.add('dim'); hint.innerHTML = '<span class="note">♪</span> you are playing the Spanish Gipsy scale'; }
  }
  canvas.addEventListener('pointerdown', function(e){ trigger(e); });
  canvas.addEventListener('pointermove', function(e){
    if (e.pointerType === 'mouse' && e.buttons === 0) return;
    if (!played) return; // require a first tap before drag-play
    trigger(e);
  });

  var rsz = null;
  window.addEventListener('resize', function(){
    clearTimeout(rsz);
    rsz = setTimeout(build, 150);
  });

  build();
  requestAnimationFrame(frame);
})();
