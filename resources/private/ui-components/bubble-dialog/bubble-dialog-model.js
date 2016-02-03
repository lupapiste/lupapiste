// Non-modal bubble alternative to dialogs.
// Parameters [optional]:
// visible: Observable flag for bubble visibility
// okFun: Callback function to be called when OK button is pressed.
// [initFun]: Callback function to be called when bubble opens (default _.noop).
// [buttonIcon]: Icon for OK button (default no icon)
// [buttonText]: Ltext for OK button (default 'ok')
// [okEnabled]: Is OK button enabled (default true). Typically observable, when given.
// [waiting]: Observable that is true when bubble is waiting/pending.
// [error]: Error message observable. The message is shown above
//          the dialog buttons.
// [prefix]: Prefix for button and error test ids.
//           [prefix-]bubble-dialog-ok
//           [prefix-]bubble-dialog-cancel
//           [prefix-]bubble-dialog-error
//           (default no prefix- part).
LUPAPISTE.BubbleDialogModel = function( params ) {
  "use strict";
  var self = this;

  var BUBBLE = "bubble-dialog";
  self.bubbleVisible = params.visible;
  self.buttonIcon = params.buttonIcon || "";
  self.buttonText = params.buttonText || "ok";
  self.okEnabled = _.isUndefined( params.okEnabled )
                 ? true
                 : params.okEnabled;

  self.ok = params.okFun;
  var initFun = params.initFun || _.noop;
  self.cancel = _.partial( self.bubbleVisible, false );
  self.waiting = params.waiting;
  self.error = params.error;
  self.prefix = params.prefix ? params.prefix + "-" : "";

  // When a bubble is opened all the other bubbles are closed.
  var bubbleId = _.uniqueId(BUBBLE + "-");
  var hubId = hub.subscribe( BUBBLE,
                             function( data ) {
                               if( data && data.id !== bubbleId ) {
                                 self.bubbleVisible( false );
                               }
                             });

  ko.computed( function() {
    if( self.bubbleVisible()) {
      hub.send( BUBBLE, {id: bubbleId});
      initFun();
    }
  });

  self.dispose = function() {
    hub.unsubscribe( hubId );
  };
};
