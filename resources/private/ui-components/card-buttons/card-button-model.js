// Card button component.
// Extends from icon-button with the following changes:
//  - Icon is not mandatory
//  - btn-card is always added to the given classes
//  - Default buttonClass is action.
//  - No test-id by default.
//
// New parameters [optional]:
//  [attr]: Extra attributes passed to the underlying button. Similar
//  to KO attr binding.
LUPAPISTE.CardButtonModel = function( params ) {
  "use strict";
  var self = this;

  ko.utils.extend( self, new LUPAPISTE.IconButtonModel( params ));

  self.testId = params.testId;
  self.buttonClass = params.buttonClass || "action";
  self.attr = _.merge( ko.unwrap( params.attr ),
                       {"class": self.buttonClass,
                        type: self.buttonType,
                        id: self.id });

  self.iconClass = self.disposedPureComputed( function() {
    var icon = ko.unwrap( params.icon );
    if( icon ) {
      return ko.unwrap( self.waiting )
        ? "icon-spin lupicon-refresh"
        : "lupicon-" + icon;
    }
  });
};
