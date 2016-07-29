var i = 0;
var mode = 0;
var prevMode = 0;
var width = 200;
var height = 50;
var steps = 29;
var threshold =19;
var step = -1; 
var offset = 20;
var lastStart = 80;

var mouseX = 0;
var mouseY = 0;

mugedaInputEvent = function(mode, event, position)
{
    mouseX = position.x;
    mouseY = position.y;
};
    
function startMove(event){
  if(mode == 2)
  {
    
    return;
  }
    
  mode = 1;
  var pt = {x:mouseX, y:mouseY};
  step = offset + Math.floor(steps*(pt.x-50)/width);
  
  event.preventDefault();
}

function inMove(event){
  if(mode == 1){
    var pt = {x:mouseX, y:mouseY};
    step =  offset + Math.floor(steps*(pt.x-50)/width);
    if(Math.min(step - threshold - offset, 0) == 0){
     step = lastStart;
     mode = 2;
    }
  }
  
  event.preventDefault();
}

function endMove(event){
  if(Math.max(step - threshold - offset, 0) == 0 && Math.max(mode-2, 0) == 0){
    mode = 0;
    step = 0;
    gotoAndPlay(0);
  }
}

onRenderReady = function(){
    var cvsPreview = document.getElementById('previewCanvas');
    if(cvsPreview){
        cvsPreview.onmousedown = startMove;
        cvsPreview.onmousemove = inMove;
        cvsPreview.onmouseup = endMove;
     
        cvsPreview.ontouchstart = startMove;
        cvsPreview.ontouchmove = inMove;
        cvsPreview.ontouchend = endMove;
    }
}
    
enterFrame = function(fid)
{
  var updated = false;
  if(mode == 1){
    if(prevMode == 0){
      prevMode = 1;
    }

    gotoAndPlay(step); 
    updated = true;
  }
  else if( fid == 19){
      gotoAndPlay(1);
      updated = true;
  }
  else if(fid == 150){
      gotoAndPlay(150);
      updated = true;
  }
  return updated;
}