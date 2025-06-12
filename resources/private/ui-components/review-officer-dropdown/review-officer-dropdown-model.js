// A component for selecting the review officer.
// Takes the form of either a text field or a dropdown select menu,
// depending on whether the organization has the review officers list
// enabled.
//
// If the list is enabled, use the list to populate the dropdown menu.
// Otherwise, allow the user to input the value as free text.
//
// When disabled, the value is always shown as disabled text input.
LUPAPISTE.ReviewOfficerDropdownModel = function(params) {
  "use strict";

  var self = this;

  ko.utils.extend(self, new LUPAPISTE.SelectFieldModel(params));

  self.useList = self.disposedPureComputed( function() {
    return !self.isDisabled()
      && lupapisteApp.models.application.reviewOfficers().enabled;
  });

  self.officers = self.disposedPureComputed( function() {
    return lupapisteApp.models.application.reviewOfficers().officers;
  });

  self.useText = self.disposedPureComputed( function() {
    return !(self.isDisabled() || self.useList() );
  });

  self.disabledValue = self.disposedPureComputed(function() {
    var v = self.value();
    return _.get( v, "name", v );
  });

  // Parameters for the select
  self.optionsValue = "code";
  self.optionsText  = params.optionsText || "name";
  self.optionsCaption = loc("selectone");

  self.selectedValue = self.disposedComputed( {
    read: function() {
      return util.getIn( self.value, [self.optionsValue]);
    },
    write: function( k ) {
      self.value( _.find( self.officers(), [self.optionsValue, k]));
    }
  });

};
