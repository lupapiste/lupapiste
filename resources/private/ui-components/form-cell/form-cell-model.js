LUPAPISTE.FormCellModel = function( params ) {
  "use strict";
  var self = this;

  self.warning = params.warning;

  self.required = params.required
    ? ko.pureComputed( function() {
      return !_.trim(params.value());
    })
  : false;

  self.isMandatory = Boolean( self.required );
  self.id = _.uniqueId( "form-cell-id-");
  self.label = params.label;
  self.componentName = "cell-" + params.cell;
  self.componentParams = _.omit( params, ["$raw", "warning", "required", "id", "label", "cell"]);
  self.componentParams.attr = _.defaults( self.componentParams.attr, {id: self.id});
};
