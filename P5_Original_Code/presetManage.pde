class PresetTable{
  Table presets;  
  float[] presetValues=new float[12];

  PresetTable(){
    presets = loadTable("Presets.csv", "header");
  }
 
  float[] loadPreset(int n){
    TableRow row = presets.getRow(n);
    String name =row.getString("Name");
    presetValues[0]=row.getFloat("Cut");
    presetValues[1]=row.getFloat("Res");
    presetValues[2]=row.getFloat("Env");
    presetValues[3]=row.getFloat("Att");
    presetValues[4]=row.getFloat("Rel");
    presetValues[5]=row.getFloat("Dis");
    presetValues[6]=row.getFloat("Fm");
    presetValues[7]=row.getFloat("Amo");
    presetValues[8]=row.getFloat("Cho");
    presetValues[9]=row.getFloat("Mod");
    presetValues[10]=row.getFloat("Del");
    presetValues[11]=row.getFloat("Fee");
    return presetValues;
  }
  
  void savePreset(String name, float[] _presetValues){
    TableRow newRow = presets.addRow();
    newRow.setString("Name",name);
    newRow.setFloat("Cut",_presetValues[0]);
    newRow.setFloat("Res",_presetValues[1]);
    newRow.setFloat("Env",_presetValues[2]);
    newRow.setFloat("Att",_presetValues[3]);
    newRow.setFloat("Rel",_presetValues[4]);
    newRow.setFloat("Dis",_presetValues[5]);
    newRow.setFloat("Fm", _presetValues[6]);
    newRow.setFloat("Amo",_presetValues[7]);
    newRow.setFloat("Cho",_presetValues[8]);
    newRow.setFloat("Mod",_presetValues[9]);
    newRow.setFloat("Del",_presetValues[10]);
    newRow.setFloat("Fee",_presetValues[11]);
    saveTable(presets,"data/Presets.csv");
  }    
}
