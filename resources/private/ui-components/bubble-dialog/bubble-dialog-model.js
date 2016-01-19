LUPAPISTE.BubbleDialogModel = function( params ) {
  "use strict";
  var self = this;

  self.bubbleVisible = params.visible;
  self.buttonIcon = params.buttonIcon || "";
  self.buttonText = params.buttonText || "ok";
  self.buttonEnabled = _.isUndefined( params.buttonEnabled )
                     ? true
                     : params.buttonEnabled;

  self.ok = params.okFun;
  self.cancel = _.partial( self.bubbleVisible, false );
  self.waiting = params.waiting;

  ko.computed( function() {
    if( !self.bubbleVisible()) {
      self.waiting( false );
    }
  });
};
