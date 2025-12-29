void drawKeys(){
     fill(122,220);
     rect(0.5*width, 0.92*height, 0.95*width, 0.25* height, int(0.03125*height), 
     int(0.03125*height), 0, 0);
     fill(122);
     rect(0.77*width, 0.745*height, 0.12*width, 0.09* height, 0.016*height, 
     0.016*height, 0.016*height, 0.016*height);
}


void drawScaleModes(){
     fill(122,220);
     rect(0.5*width, 0.035*height, 0.95*width, 0.18*height, 0, 0, int(0.03125*height), 
     int(0.03125*height));
}


void drawPointerModes(){
     fill(122,220);
     rect(0.04*width, 0.5*height, 0.15*width, 0.55*height, 0, int(0.03125*height), 
     int(0.03125*height), 0);
   
}

void drawSynthControl(){
  fill(122,220);
  rect(0.89*width, 0.5*height, 0.3*width, 0.75*height, int(0.03125*height), 0, 0, 
  int(0.03125*height));
  
}
