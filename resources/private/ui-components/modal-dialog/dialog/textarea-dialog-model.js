LUPAPISTE.TextareaDialogModel = function (params) {
  "use strict";
  var self = this;

  ko.utils.extend( self, new LUPAPISTE.ComponentBaseModel() );

  self.id = _.uniqueId( "textarea-dialog" );

  self.text = params.ltext ? loc(params.ltext) : params.text;

  self.yesTitle = params.lyesTitle ? loc(params.lyesTitle) : params.yesTitle;

  self.noTitle = params.lnoTitle ? loc(params.lnoTitle) : params.noTitle;

  self.yes = params.yesFn || _.noop;

  self.no = params.noFn || _.noop;

  self.mandatory = params.mandatory;

  self.textarea = params.textarea;
  self.textarea.label = params.textarea.llabel ? loc(params.textarea.llabel) : params.textarea.label;

  self.yesEnabled = self.disposedPureComputed( function() {
    return self.mandatory ? !_.isBlank( self.textarea.observable()) : true;
  });
};
