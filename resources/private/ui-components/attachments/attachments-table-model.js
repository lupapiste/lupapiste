LUPAPISTE.AttachmentsTableModel = function( params ) {
  "use strict";
  var self = this;
  ko.utils.extend( self, new LUPAPISTE.ComponentBaseModel());

  self.attachments = params.attachments;

  var idPrefix = _.uniqueId( "at-input-");

  self.hasFile = function( data ) {
    return _.get( data, "latestVersion.fileId");
  };

  self.stateIcons = function( data ) {
    return "icon";
  };

  self.isFiltered = function( data ) {
    return false;
  };

  self.inputId = function( index ) {
    return idPrefix + index;
  };
};
