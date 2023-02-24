LUPAPISTE.InputFieldModel = function(params) {
  "use strict";

  var self = this;

  ko.utils.extend( self, new LUPAPISTE.EnableComponentModel( params ));

  params = params || {};

  self.id = params.id || _.uniqueId( "input-field" );
  self.errorId = self.id + "-error";
  self.name = params.name;
  self.label = params.lLabel ? loc(params.lLabel) : params.label;
  self.hasHtmlLabel = params.hasHtmlLabel;
  self.value = params.value;
  self.placeholder = params.lPlaceholder ? loc(params.lPlaceholder) : params.placeholder;
  self.isSelected = params.hasFocus || ko.observable();
  self.maxlength = params.maxlength || "";

  self.required = self.disposedPureComputed( _.wrap( self.value,
                                                     util.isRequiredObservable ));
  self.notValid = self.disposedPureComputed( _.wrap( self.value,
                                                     util.isNotValidObservable ));

  self.disable = self.isDisabled;

  self.infoMsg = params.infoMsg || "";
  self.infoStyle = params.infoStyle || "";
  self.extraClass = params.extraClass;

};
