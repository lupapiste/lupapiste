// Simple in-place notification mechanism.
// Flickers the indicator info.
// Params [optional]:
//
//  indicator: Observable that can have the following properties:
//
//             ltext: L10n key for the message
//             text: Raw message. Overridden by ltext.
//
//             style/type (both keys are supported): Notifcation
//             style. Supported values are positive (default),
//             negative and primary.
//  mode: Either 'text', 'icon' or 'both' (default).
//
//  Due to legacy reasons, if ltext/text is not given, the ltext is
//  determined by the notification style: "form.err" (negative) and
//  "form.saved" (positive). If the text cannot be resolved or it is
//  empty, only notification icon is shown.

LUPAPISTE.PING_DEFAULTS = {texts: {negative: "form.err",
                                   positive: "form.saved"},
                           icons: {negative: "warning",
                                   positive: "check",
                                   primary: "circle-info"}};

LUPAPISTE.PingModel = function( params ) {
  "use strict";
  var self = this;

  ko.utils.extend( self, new LUPAPISTE.ComponentBaseModel());

  self.show = ko.observable();
  var style = ko.observable();
  self.text = ko.observable();
  var mode =  params.mode || "both";

  function pingStyle( indicator ) {
    var s = _.get( indicator, "type" ) || _.get( indicator, "style" );
    if( _.includes( ["err", "negative"], s)) {
      style( "negative" );
    } else {
      style( s === "primary" ? s : "positive" );
    }
  }

  function pingText( indicator ) {
    if( mode !== "icon" ) {
      var ltext = _.get( indicator, "ltext" );
      var text = ltext ? loc( ltext ) : _.get( indicator, "text");
      self.text( _.isString( text ) ? text : loc( LUPAPISTE.PING_DEFAULTS.texts[style()] ));
    }
  }

  self.wrapperCss = self.disposedPureComputed( function() {
    return _.set( {}, "ping--" + style(), true );
  });



  self.disposedSubscribe( params.indicator, function( indicator ) {
    pingStyle( indicator );
    pingText( indicator );
    self.show( true );
  });

  self.disposedSubscribe( self.show, function( show ) {
    if( show ) {
      _.delay( self.show, 1000, false );
    }
  });

  self.iconClass = self.disposedPureComputed( function() {
    if( mode !== "text" ) {
      return "lupicon-" + LUPAPISTE.PING_DEFAULTS.icons[style()];
    }
  });
};
