import processing.video.*;
import org.puredata.processing.PureData;
import processing.opengl.*;

//works on 2.0b8 at least!

Capture video;
PureData pd;

PresetTable presets; //this is not being used by now

RadioButtons modes, keys, scales, octaves, figures;
Slider sCutoff, sResonance, sEnvelope, sAttack, sRelease, sLevel;
Slider FM, amountFM, modChor, freqChor, delayFig, Lfeedback;
Toggle tSub, tSin, tSaw, tSqr, tNoi;
Button tapButton; 

boolean modesShown=false;
boolean scalesShown=false;
boolean keysShown=false;
boolean synthShown=false;

int blockSize=20;
int cols, rows;

int spaceSize=40;

int index = 0;
int nNotes;

int cmd = 1;

float rotFact = 0;
boolean backFact = true;
boolean randFact = false;

float periodTempo = 500;
float pastmillis = 0;
float ippast=0;
float jppast=0;
int octave[]= {
  1, 2, 4, 8, 16, 32, 64
};

float rOut, gOut, bOut;

//float meanBrightness = 0;

void setup() {

  PImage seqInactive = loadImage("graphic/seq_off.png");
  PImage seqActive = loadImage("graphic/seq_on.png");
  PImage cntInactive = loadImage("graphic/cnt_off.png");
  PImage cntActive = loadImage("graphic/cnt_on.png");
  PImage briInactive = loadImage("graphic/bri_off.png");
  PImage briActive = loadImage("graphic/bri_on.png");
  PImage pntInactive = loadImage("graphic/pnt_off.png");
  PImage pntActive = loadImage("graphic/pnt_on.png");

  PImage keyActive = loadImage("graphic/key_on.png");
  PImage blackeyActive = loadImage("graphic/blackey_on.png");
  PImage keyInactive = loadImage("graphic/key_off.png");
  PImage blackeyInactive = loadImage("graphic/blackey_off.png");

  PImage aeolianInactive = loadImage("graphic/Aeolian_off.png");
  PImage aeolianActive = loadImage("graphic/Aeolian_on.png");
  PImage dorianInactive = loadImage("graphic/Dorian_off.png");
  PImage dorianActive = loadImage("graphic/Dorian_on.png");
  PImage phrygianInactive = loadImage("graphic/Phrygian_off.png");
  PImage phrygianActive = loadImage("graphic/Phrygian_on.png");
  PImage lydianInactive = loadImage("graphic/Lydian_off.png");
  PImage lydianActive = loadImage("graphic/Lydian_on.png");
  PImage mixolydianInactive = loadImage("graphic/Mixolydian_off.png");
  PImage mixolydianActive = loadImage("graphic/Mixolydian_on.png");
  PImage ionianInactive = loadImage("graphic/Ionian_off.png");
  PImage ionianActive = loadImage("graphic/Ionian_on.png");
  PImage locrianInactive = loadImage("graphic/Locrian_off.png");
  PImage locrianActive = loadImage("graphic/Locrian_on.png"); 
  PImage gipsyInactive = loadImage("graphic/Gipsy_off.png");
  PImage gipsyActive = loadImage("graphic/Gipsy_on.png");
  PImage hawaiInactive = loadImage("graphic/Hawai_off.png");
  PImage hawaiActive = loadImage("graphic/Hawai_on.png");
  PImage bluesInactive = loadImage("graphic/Blues_off.png");
  PImage bluesActive = loadImage("graphic/Blues_on.png");
  PImage harmInactive = loadImage("graphic/Harm_off.png");
  PImage harmActive = loadImage("graphic/Harm_on.png");
  PImage japanInactive = loadImage("graphic/Japan_off.png");
  PImage japanActive = loadImage("graphic/Japan_on.png");


  PImage redondaInactive = loadImage("graphic/redonda_off.png");
  PImage redondaActive = loadImage("graphic/redonda_on.png");
  PImage blancaInactive = loadImage("graphic/blanca_off.png");
  PImage blancaActive = loadImage("graphic/blanca_on.png");
  PImage negraInactive = loadImage("graphic/negra_off.png");
  PImage negraActive = loadImage("graphic/negra_on.png");
  PImage corcheaInactive = loadImage("graphic/corchea_off.png");
  PImage corcheaActive = loadImage("graphic/corchea_on.png");
  PImage semicorcheaInactive = loadImage("graphic/semicorchea_off.png");
  PImage semicorcheaActive = loadImage("graphic/semicorchea_on.png");
  PImage fusaInactive = loadImage("graphic/fusa_off.png");
  PImage fusaActive = loadImage("graphic/fusa_on.png");
  PImage semifusaInactive = loadImage("graphic/semifusa_off.png");
  PImage semifusaActive = loadImage("graphic/semifusa_on.png");

  PImage sinInactive = loadImage("graphic/sin_off.png");
  PImage sinActive = loadImage("graphic/sin_on.png");
  PImage sawInactive = loadImage("graphic/saw_off.png");
  PImage sawActive = loadImage("graphic/saw_on.png");
  PImage sqrInactive = loadImage("graphic/sqr_off.png");
  PImage sqrActive = loadImage("graphic/sqr_on.png");
  PImage subInactive = loadImage("graphic/sub_off.png");
  PImage subActive = loadImage("graphic/sub_on.png");
  PImage noiInactive = loadImage("graphic/noi_off.png");
  PImage noiActive = loadImage("graphic/noi_on.png");

  PImage oct1Active = loadImage("graphic/octave1_on.png");
  PImage oct1Inactive = loadImage("graphic/octave1_off.png");
  PImage oct2Active = loadImage("graphic/octave2_on.png");
  PImage oct2Inactive = loadImage("graphic/octave2_off.png");
  PImage oct3Active = loadImage("graphic/octave3_on.png");
  PImage oct3Inactive = loadImage("graphic/octave3_off.png");
  PImage oct4Active = loadImage("graphic/octave4_on.png");
  PImage oct4Inactive = loadImage("graphic/octave4_off.png");
  PImage oct5Active = loadImage("graphic/octave5_on.png");
  PImage oct5Inactive = loadImage("graphic/octave5_off.png");
  PImage oct6Active = loadImage("graphic/octave6_on.png");
  PImage oct6Inactive = loadImage("graphic/octave6_off.png");
  PImage oct7Active = loadImage("graphic/octave7_on.png");
  PImage oct7Inactive = loadImage("graphic/octave7_off.png");

  PImage tapInactive = loadImage("graphic/tapButton_off.png");
  PImage tapActive = loadImage("graphic/tapButton_on.png");


  String[] modeNames = {
    "sequence", "brightest", "center", "pointer"
  };
  modes = new RadioButtons(modeNames, 4, int(0.015*width), int(0.25*height), 
  int(0.12*height), int(0.12*height), VERTICAL);
  PImage[] inactiveButtons = {
    seqInactive, briInactive, cntInactive, pntInactive
  };
  PImage[] activeButtons = {
    seqActive, briActive, cntActive, pntActive
  };
  modes.setAllInactiveImages(inactiveButtons);
  modes.setAllActiveImages(activeButtons);
  modes.set("sequence");

  String[] keyNames = {
    "C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", 
    "A", "A#", "B"
  };
  keys = new RadioButtons(keyNames, 12, int(0.055*width), int(0.8*height), 
  int(0.068*width), int(0.2*height), HORIZONTAL);
  PImage[] activeKeys = {
    keyActive, blackeyActive, keyActive, blackeyActive, keyActive, 
    keyActive, blackeyActive, keyActive, blackeyActive, keyActive, blackeyActive, keyActive
  };
  PImage[] inactiveKeys = {
    keyInactive, blackeyInactive, keyInactive, blackeyInactive, keyInactive, 
    keyInactive, blackeyInactive, keyInactive, blackeyInactive, keyInactive, blackeyInactive, keyInactive
  };
  keys.setAllInactiveImages(inactiveKeys);
  keys.setAllActiveImages(activeKeys);
  keys.set("C");

  String[] scaleNames = {
    "Ionian", "Dorian", "Phrygian", "Lydian", "MixoLydian", "Aeolian", "Locrian", "HarmMin", 
    "Gipsy", "Hawaian", "Blues", "Japanese"
  };
  scales = new RadioButtons(scaleNames, 12, int(0.055*width), int(0.025*height), 
  int(0.068*width), int(0.08*height), HORIZONTAL);
  PImage[] activeScales = {
    ionianActive, dorianActive, phrygianActive, lydianActive, mixolydianActive, aeolianActive, 
    locrianActive, harmActive, gipsyActive, hawaiActive, bluesActive, japanActive
  };
  PImage[] inactiveScales = {
    ionianInactive, dorianInactive, phrygianInactive, lydianInactive, mixolydianInactive, 
    aeolianInactive, locrianInactive, harmInactive, gipsyInactive, hawaiInactive, bluesInactive, japanInactive
  };
  scales.setAllInactiveImages(inactiveScales);
  scales.setAllActiveImages(activeScales);
  scales.set("Gipsy");

  String[] figureNames = {
    "Redonda", "Blanca", "Negra", "Corchea", "SemiCorchea", "Fusa", "SemiFusa"
  };
  figures = new RadioButtons(figureNames, 7, int(0.1*width), int(0.7*height), 
  int(0.068*width), int(0.09*height), HORIZONTAL);
  PImage[] activeFigures = {
    redondaActive, blancaActive, negraActive, corcheaActive, 
    semicorcheaActive, fusaActive, semifusaActive
  };
  PImage[] inactiveFigures = {
    redondaInactive, blancaInactive, negraInactive, 
    corcheaInactive, semicorcheaInactive, fusaInactive, semifusaInactive
  };
  figures.setAllInactiveImages(inactiveFigures);
  figures.setAllActiveImages(activeFigures);
  figures.set("Negra");

  String[] octaveNames = {
    "x1", "x2", "x3", "x4", "x5", "x6", "x7"
  };
  octaves = new RadioButtons(octaveNames, 7, int(0.1*width), int(0.6*height), 
  int(0.068*width), int(0.09*height), HORIZONTAL);
  PImage[] activeOctaves = {
    oct1Active, oct2Active, oct3Active, oct4Active, oct5Active, 
    oct6Active, oct7Active
  };
  PImage[] inactiveOctaves = {
    oct1Inactive, oct2Inactive, oct3Inactive, oct4Inactive, 
    oct5Inactive, oct6Inactive, oct7Inactive
  };
  octaves.setAllInactiveImages(inactiveOctaves);
  octaves.setAllActiveImages(activeOctaves);
  octaves.set("x3");


  sCutoff = new Slider("Cutoff", 1, 0, 1, int(0.78*width), int(0.24*height), 
  int(0.312*width), int(0.03125*height), HORIZONTAL);
  sCutoff.setInactiveColor(color(120, 80, 80));
  sResonance = new Slider("Resonance", 0, 0, 1, int(0.78*width), int(0.295*height), 
  int(0.312*width), int(0.03125*height), HORIZONTAL);
  sResonance.setInactiveColor(color(120, 80, 80));
  sEnvelope = new Slider("fEnvelope", 0, -1, 1, int(0.78*width), int(0.35*height), 
  int(0.312*width), int(0.03125*height), HORIZONTAL);
  sEnvelope.setInactiveColor(color(120, 80, 80));
  sAttack = new Slider("Attack", 0, 0, 1, int(0.78*width), int(0.405*height), 
  int(0.312*width), int(0.03125*height), HORIZONTAL);
  sAttack.setInactiveColor(color(120, 80, 80));
  sRelease = new Slider("Release", 0.2, 0, 1, int(0.78*width), int(0.46*height), 
  int(0.312*width), int(0.03125*height), HORIZONTAL);
  sRelease.setInactiveColor(color(120, 80, 80));
  sLevel = new Slider("Distortion", 0, 0, 1, int(0.78*width), int(0.515*height), 
  int(0.312*width), int(0.03125*height), HORIZONTAL);
  sLevel.setInactiveColor(color(120, 80, 80));
  FM = new Slider("FM", 0, 0, 1, int(0.78*width), int(0.57*height), 
  int(0.312*width), int(0.03125*height), HORIZONTAL);
  FM.setInactiveColor(color(120, 80, 80));
  amountFM = new Slider("Amount", 0, 0, 1, int(0.78*width), int(0.625*height), 
  int(0.312*width), int(0.03125*height), HORIZONTAL);
  amountFM.setInactiveColor(color(120, 80, 80));
  freqChor = new Slider("ChorFREQ", 0, 0, 1, int(0.78*width), int(0.68*height), 
  int(0.312*width), int(0.03125*height), HORIZONTAL);
  freqChor.setInactiveColor(color(120, 80, 80));
  modChor = new Slider("ChorMOD", 0, 0, 1, int(0.78*width), int(0.735*height), 
  int(0.312*width), int(0.03125*height), HORIZONTAL);
  modChor.setInactiveColor(color(120, 80, 80));
  delayFig = new Slider("DelayFIG", 1, -2, 4, int(0.78*width), int(0.79*height), 
  int(0.312*width), int(0.03125*height), HORIZONTAL);
  delayFig.setInactiveColor(color(120, 80, 80));
  Lfeedback = new Slider("Feedback", 0.5, 0, 1, int(0.78*width), int(0.845*height), 
  int(0.312*width), int(0.03125*height), HORIZONTAL);
  Lfeedback.setInactiveColor(color(120, 80, 80));


  tSub = new Toggle("SUB", int(0.77*width), int(0.16*height), int(0.04*height), 
  int(0.04*height));
  tSin = new Toggle("SIN", int(0.815*width), int(0.16*height), int(0.04*height), 
  int(0.04*height));
  tSaw = new Toggle("SAW", int(0.86*width), int(0.16*height), int(0.04*height), 
  int(0.04*height));
  tSqr = new Toggle("SQR", int(0.905*width), int(0.16*height), int(0.04*height), 
  int(0.04*height));
  tNoi = new Toggle("NSE", int(0.95*width), int(0.16*height), int(0.04*height), 
  int(0.04*height));                 
  tSub.setInactiveImage(subInactive);
  tSub.setActiveImage(subActive);
  tSin.setInactiveImage(sinInactive);
  tSin.setActiveImage(sinActive);
  tSqr.setInactiveImage(sqrInactive);
  tSqr.setActiveImage(sqrActive);
  tSaw.setInactiveImage(sawInactive);
  tSaw.setActiveImage(sawActive);
  tNoi.setInactiveImage(noiInactive);
  tNoi.setActiveImage(noiActive);
  tSub.set(true);
  tSin.set(true);
  tSqr.set(false);
  tSaw.set(false);
  tNoi.set(false);

  tapButton = new Button("TapTempo", int(0.62*width), int(0.685*height), 
  int(0.12*height), int(0.12*height));
  tapButton.setInactiveImage(tapInactive);
  tapButton.setActiveImage(tapActive);


  pd = new PureData(this, 44100, 0, 2);
  pd.openPatch("patch/STEPPEDPIX.pd");
  pd.subscribe("BANG");
  pd.start();

  size (800, 600, P3D);

  video = new Capture(this, width, height);
  video.start();

  pd.sendFloat("onSEQ", 1);
  pd.sendFloat("periodSEQ", periodTempo); 

  pd.sendFloat("Sub", bool2float(tSub.get()));
  pd.sendFloat("Sin", bool2float(tSin.get()));
  pd.sendFloat("Saw", bool2float(tSaw.get()));
  pd.sendFloat("Sqr", bool2float(tSqr.get()));
  pd.sendFloat("Noi", bool2float(tNoi.get())); 
  pd.sendFloat("Rsend", 0.2);

  noStroke();
}


void draw() {

  if (video.available()) {
    video.read();
    video.loadPixels();

    float tinteout=0, satout=0;
    float brightest=0;
    float iB=0, jB=0;

    cols = width / blockSize;
    rows = height / blockSize;

    if (backFact) background(rOut, gOut, bOut, 55);
    else background(tinteout); 


    for (int i = 0; i < cols; i++) {
      for (int j = 0; j < rows; j++) {

        int x = i*blockSize;
        int y = j*blockSize;
        int loc = (video.width - x - 1) + y*video.width;

        color c= video.pixels[loc];
        float r = (c >> 16) & 0xFF;
        float g = (c >> 8) & 0xFF;
        float b = c & 0xFF;

        c = color(r, g, b, 200);

        float bTresh = brightness(c);
        float tinte = hue(c);
        float sat = saturation(c);

        switch (cmd) {         
        case 1: 
          if ( index== (i*rows + j)) {
            brightest = bTresh;
            iB = i;
            jB= j;
            tinteout= tinte;
            satout = sat;
            rOut = r;
            gOut = g;
            bOut = b;
            rotFact = 0;
            backFact = true;
            randFact = true;
          } 
          break;

        case 2: 
          if ( bTresh > brightest) {
            brightest = bTresh;
            iB = i;
            jB= j;
            tinteout= tinte;
            rOut = r;
            gOut = g;
            bOut = b;
            rotFact = 1;
            backFact = true;
            randFact = false;
          } 
          break;

        case 3: 
          if ( i == int(cols/2) && j == int(rows/2) ) {
            brightest = bTresh;
            iB = i;
            jB= j;
            tinteout= tinte;
            rOut = r;
            gOut = g;
            bOut = b;
            rotFact = 1;
            backFact = false;
            randFact= false;
          } 
          break;

        case 4: 
          if ( i == mouseX/blockSize && j == mouseY/blockSize ) {
            brightest = bTresh;
            iB = i;
            jB= j;
            tinteout= tinte;
            rOut = r;
            gOut = g;
            bOut = b;
            rotFact=0;
            backFact=false;
            randFact = true;
            //if (mousePressed) pd.sendBang("BANG");
            //pd.sendFloat("NoteOn",1);
            //else pd.sendFloat("NoteOn",0);
          } 
          break;
        }
        pushMatrix();  
        float mouseMove = map(mouseX,0,width,-100,100);
        float BrightMap=map(bTresh, 0, 255, 0, 10);
        translate(x+blockSize/2, y+blockSize/2, BrightMap*-10);
        rectMode(CENTER);
        rotate(map(tinte*rotFact, 0, 255, 0, TWO_PI));
        fill(c);
        
        //int var=int(random(2));
        float vari = (i*j) % (int(sCutoff.get()*10)+1);

        if ((cmd==2) || (cmd==3)) {
          if (vari==0) rect(0, 0, blockSize*BrightMap, blockSize*BrightMap);
          else ellipse(0, 0, blockSize*BrightMap, blockSize*BrightMap);
        }
        else rect(0, 0, (blockSize*0.6+(0.4*bTresh)), (blockSize*0.6+(0.4*bTresh)));
          //sphere(blockSize*0.6+(0.4*bTresh));
        popMatrix(); 
        //meanBrightness+=bTresh;
      }
    }
    if (index >= cols*rows) index = 0; 
    float bMap = map(brightest, 0, 255, 0, 3); 
    fill(abs(rOut-255), abs(gOut-255), abs(bOut-255), 220);
    rect(iB*blockSize + blockSize/2, jB*blockSize + blockSize/2, blockSize*bMap, blockSize*bMap);  
    //println(meanBrightness/(cols*rows));

    //meanBrightness=0;

    if (mouseY > 0.98*height) keysShown=true;
    if ((mouseY < 0.6*height) || (mouseX < 0.03*width) || (mouseX > 0.97*width)) keysShown=false;

    if (mouseY < 0.02*height) scalesShown=true;
    if ((mouseY > 0.12*height) || (mouseX < 0.03*width) || (mouseX > 0.97*width)) scalesShown=false; 

    if (mouseX < 0.02*width) modesShown=true;
    if ((mouseX > 0.12*width) || (mouseY < 0.03*width) || (mouseY > 0.97*width)) modesShown=false;

    if (mouseX>0.98*width) synthShown=true;
    if ((mouseX < 0.75*width) || (mouseY < 0.03*width) || (mouseY > 0.97*width))  synthShown=false;


    if (keysShown) {
      drawKeys(); 
      figures.display();
      keys.display();
      fill(0, 255, 255);
      text(truncate(Tempo(periodTempo)), 0.7225*width, 0.755*height);
      tapButton.display();
      octaves.display();
    } 

    if (scalesShown) {
      drawScaleModes(); 
      scales.display();
    } 

    if (modesShown) {
      drawPointerModes();
      modes.display();
    }

    if (synthShown) {
      drawSynthControl();
      tSub.display();
      tSin.display();
      tSaw.display();
      tSqr.display(); 
      tNoi.display();
      sCutoff.display();
      sResonance.display(); 
      sEnvelope.display();
      sAttack.display(); 
      sRelease.display();
      sLevel.display();  
      FM.display();
      amountFM.display();
      freqChor.display();
      modChor.display();
      delayFig.display();
      Lfeedback.display();
    } 

    if (scales.get()<=9) nNotes = 13;
    if (scales.get()==10) nNotes = 11;
    if (scales.get()==11) nNotes = 9;

    float rootFreq = 440 * pow(2, (-45+keys.get())/12.);
    int chromStep = round(map(tinteout, 0, 255, 0, nNotes));
    float freq = octave[octaves.get()] * rootFreq * diatonicStep[scales.get()][chromStep];  

    float ip = (iB/cols)*spaceSize - spaceSize/2;
    float jp = (jB/rows)*spaceSize - spaceSize/2;

    float cutoff = freq/2+16000*pow(sCutoff.get(), 4);
    float envdif = cutoff*pow(2, 4*sEnvelope.get())-cutoff;
    pd.sendFloat("X", ip);
    pd.sendFloat("Y", jp+0.1);
    pd.sendFloat("Freq", freq);
    pd.sendFloat("Gain", brightest/255*0.5);

    pd.sendFloat("Cutoff", cutoff);
    pd.sendFloat("Resonance", 1+100*pow(sResonance.get(), 3));
    pd.sendFloat("Envelope", envdif);
    pd.sendFloat("Attack", 5+sAttack.get()*500);
    pd.sendFloat("Release", sRelease.get()*5000);
    pd.sendFloat("Dist", sLevel.get());
    pd.sendFloat("FM", 8000*pow(FM.get(), 2));
    pd.sendFloat("amountFM", amountFM.get());
    pd.sendFloat("freqChor", 10*pow(freqChor.get(), 2));
    pd.sendFloat("modChor", 100*pow(modChor.get(), 3));
    pd.sendFloat("Lfeedback", 2.5*Lfeedback.get());
    pd.sendFloat("Rsend", Lfeedback.get()/5);
    
    pd.sendFloat("Tdelay", periodTempo/pow(2, round(delayFig.get())));
    pd.sendFloat("periodSEQ", (periodTempo/pow(2, (figures.get()-2)) ));
    pd.sendFloat("BPDFreq", rootFreq*32);
    
    //blockSize = int(sLevel.get()*100)+1;

    if ((cmd==4)&&(jp!=jppast)&&(ip!=ippast)) {
      pd.sendBang("BANG");
      jppast =jp;
      ippast =ip;
    }
  }
}


void mousePressed() {
  if (modesShown) modes.mousePressed();
  if (scalesShown) scales.mousePressed();
  if (keysShown) {
    keys.mousePressed();
    figures.mousePressed();
    octaves.mousePressed();
    if (tapButton.mousePressed()) periodTempo = tapPeriod();
  }
  if (synthShown) {
    sCutoff.mousePressed();
    sResonance.mousePressed();
    sEnvelope.mousePressed();
    sAttack.mousePressed();
    sRelease.mousePressed();
    sLevel.mousePressed();
    tSub.mousePressed();
    tSin.mousePressed();
    tSaw.mousePressed();
    tSqr.mousePressed(); 
    tNoi.mousePressed();
    FM.mousePressed();
    amountFM.mousePressed();
    freqChor.mousePressed();
    modChor.mousePressed();
    delayFig.mousePressed();
    Lfeedback.mousePressed();
  } 
  if (cmd==4) {
    pd.sendFloat("NoteOn", 1);
    pd.sendBang("BANG");
  }
}

void mouseDragged() {
  if (synthShown) {
    sCutoff.mouseDragged();
    sResonance.mouseDragged();
    sEnvelope.mouseDragged();
    sAttack.mouseDragged();
    sRelease.mouseDragged();
    sLevel.mouseDragged();
    tSub.mouseDragged();
    tSin.mouseDragged();
    tSaw.mouseDragged();
    tSqr.mouseDragged(); 
    tNoi.mouseDragged();
    FM.mouseDragged();
    amountFM.mouseDragged();
    freqChor.mouseDragged();
    modChor.mouseDragged();
    delayFig.mouseDragged();
    Lfeedback.mouseDragged();
  }
}

void mouseReleased() {
  if (modesShown) {
    if (modes.mouseReleased()) {
      switch( modes.get() ) {
      case 0: 
        cmd=1; 
        blockSize=20; 
        pd.sendFloat("onSEQ", 1); 
        break;
      case 1: 
        cmd=2; 
        blockSize=10; 
        pd.sendFloat("onSEQ", 1); 
        break;
      case 2: 
        cmd=3; 
        blockSize=20; 
        pd.sendFloat("onSEQ", 1); 
        break;
      case 3: 
        cmd=4; 
        blockSize=10; 
        pd. sendFloat("onSEQ", 0); 
        break;
      }
    }
  } 

  if (scalesShown) scales.mouseReleased();

  if (keysShown) {
    keys.mouseReleased();
    figures.mouseReleased();
    octaves.mouseReleased();
    tapButton.mouseReleased();
  }
  if (synthShown) {
    sCutoff.mouseReleased();
    sResonance.mouseReleased();
    sEnvelope.mouseReleased();
    sAttack.mouseReleased();
    sRelease.mouseReleased();
    sLevel.mouseReleased(); 
    if (tSub.mouseReleased()) pd.sendFloat("Sub", bool2float(tSub.get()));
    if (tSin.mouseReleased()) pd.sendFloat("Sin", bool2float(tSin.get()));
    if (tSaw.mouseReleased()) pd.sendFloat("Saw", bool2float(tSaw.get()));
    if (tSqr.mouseReleased()) pd.sendFloat("Sqr", bool2float(tSqr.get()));
    if (tNoi.mouseReleased()) pd.sendFloat("Noi", bool2float(tNoi.get()));
    FM.mouseReleased();
    amountFM.mouseReleased();
    freqChor.mouseReleased();
    modChor.mouseReleased();
    delayFig.mouseReleased();
    Lfeedback.mouseReleased();
  }

  if (cmd==4) pd.sendFloat("NoteOn", 0);
}

void receiveBang(String source) {
  index++;
}

float bool2float(boolean bool) {
  float castbool;
  if (bool == false) castbool = 0;
  else castbool = 1;
  return castbool;
}

float tapPeriod() {
  float period = 0;
  if (tapButton.mousePressed()) {
    period = millis() - pastmillis;
    pastmillis = millis();
    //delay(100);
  }
  if ( period > 200 && period < 2000) return period;
  else return periodTempo;
}

float truncate( float x ) {
  return round( x * 100.0f ) / 100.0f;
}

float Tempo(float periodTempo) {
  float Tempo = (1/periodTempo) * 1000 * 60;
  return Tempo;
}
