// Component that shows the given lhtml when the
// help icon is clicked.
// Parameters [optional]:
//  [show]: (Boolean) if given and true, the help is expanded by default.
//  [flag]: (ko.observable Boolean) if given, then the component
//          syncs its state with it. This is useful if the help
//          needs to be toggled outside of component.
//  [lbutton] (l10n key) if given, toggle is rendered similarly to link-btn
//  lhtml:  (l10n key) identifier for lhtml binding. Can also be an array
//          of (paragraph) identifiers.
//  html:   Full-rendered help contents.
//
// Note: lhtml and html parameters are mutually exclusive.

LUPAPISTE.HelpToggleModel = function( params ) {
  "use strict";
  var self = this;

  self.flag = params.flag || ko.observable( params.show );
  self.toggleHelp = function() {
    self.flag( !self.flag() );
  };
  var paragraph = _.template( "<p><%= p %></p>");

  function localizedHtml( lhtml ) {
    return _( [lhtml])
      .flatten()
      .map( function( s ) {
        return paragraph( {p: loc( s )});
      })
      .value()
      .join("");
  }

  self.html = params.lhtml ? localizedHtml( params.lhtml ) : params.html;
  self.lbutton = params.lbutton;
};
