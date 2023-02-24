// Convenience component for a button with icon.
// Parameters [optional]:
//  text: Button text (and aria-label.)
//  ltext: Button ltext. Ltext overrides text if both are given.
//  [icon]: Lupicon without prefix: check -> lupicon-check. Sometimes
//  it is useful to create an icon-button without icon. For example,
//  when only waiting state has icon.
//  [buttonClass]. Default primary.
//  click: Similar to KO binding
//  [right]: If true, the icon is on the right side (default false)
//  [id]: Button id
//  [testId]: Button test id (icon-button)
//  [enable]: Similar to KO binding (default true)
//  [disable]: Similar to binding (false)
//  [waiting]: Waiting observable (false)
//  [type] Button type (button)
//  [iconOnly]: Show only icon and not the text (default false). The
//  button text is still used for aria-label.
//  [focusNext]: Explicit focus target for tab. See ko.init.js for
//  details on focusNext binding.
//  [focusPrevious]: Explicit focus target for shift + tab. See ko.init.js for
//  details on focusPrevious binding.
//  [visible]: Whether button is in the DOM (default true)
//  [hidden]: Whether button is in the DOM (default false)
//  [ariaLtext/ariaText]: By default the aria-label is the same as the
//  button text, but the label can also be given explicitly.
//  [attr]: Similar to KO attr binding. The values are button attributes.
//  Button is disabled when waiting.
LUPAPISTE.IconButtonModel = function( params ) {
  "use strict";
  var self = this;

  ko.utils.extend( self, new LUPAPISTE.ComponentBaseModel());

  self.right = params.right || false;
  self.waiting = params.waiting || false;
  self.testId = params.testId || "icon-button";

  // Boolean if given, undefined otherwise
  function resolveFlag( paramName ) {
    return _.has( params, paramName )
      ? Boolean( ko.unwrap( params[paramName]))
      : undefined;
  }

  // Waiting and disble override enable.
  self.isDisabled = self.disposedPureComputed( function() {
    var enable = resolveFlag( "enable" );
    var disable = resolveFlag( "disable" );
    var waiting = resolveFlag( "waiting" );
    return waiting
      || disable
      || (_.isBoolean( enable) ? !enable : false );
  });

  // Hidden overrides visible
  self.isVisible = self.disposedPureComputed( function() {
    var visible = resolveFlag( "visible" );
    var hidden = resolveFlag( "hidden" );
    return !( hidden
              || (_.isBoolean( visible) ? !visible : false));
  });

  self.click = params.click;
  self.iconClass = self.disposedPureComputed( function() {
    var icon = ko.unwrap( params.icon );
    return ko.unwrap( self.waiting )
      ? "icon-spin lupicon-refresh"
      : (icon && "lupicon-" + icon);
  });

  self.buttonText = self.disposedPureComputed( function() {
    var ltext = ko.unwrap( params.ltext );
    return ltext ? loc( ltext ) : ko.unwrap( params.text );
  });

  self.ariaLabel = self.disposedPureComputed( function() {
    var txt = params.ariaLtext ? loc( params.ariaLtext ) : params.ariaText;
    return txt || self.buttonText();
  });

  self.showText = self.buttonText() && !params.iconOnly;
  self.focusNext = params.focusNext;
  self.focusPrevious = params.focusPrevious;

  self.attr = self.disposedPureComputed( function() {
    return _.merge( {"class": params.buttonClass || "primary",
                     "type": params.type || "button",
                     id: params.id},
                    ko.unwrap(params.attr) );
  });
};
