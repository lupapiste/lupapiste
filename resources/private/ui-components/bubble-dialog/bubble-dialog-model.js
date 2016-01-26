// Non-modal bubble alternative to dialogs.
// Parameters [optional]:
// visible: Observable flag for bubble visibility
// okFun: Callback function to be called when OK button is pressed.
// [buttonIcon]: Icon for OK button (default no icon)
// [buttonText]: Ltext for OK button (default 'ok')
// [buttonEnabled]: Is OK button initially enabled (default true)
// [waiting]: Observable that is true when bubble is waiting/pending.
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
