LUPAPISTE.InspectionSummaryTemplateBubbleModel = function(params) {
  "use strict";
  var self = this;

  ko.utils.extend(self, new LUPAPISTE.ComponentBaseModel());

  self.templateId = ko.observable();
  self.name = ko.observable("");
  self.templateText = ko.observable("");
  self.bubbleVisible = params.bubbleVisible;

  self.okVisible = ko.observable(true);
  self.cancelVisible = ko.observable(true);

  self.isValid = self.disposedPureComputed(function() {
    return !_.isEmpty(self.name()) && !_.isEmpty(self.templateText());
  });

  self.create = function() {
    ajax.command("modify-inspection-summary-template",
      {func: "create", name: self.name(), templateText: self.templateText()})
      .success(function(event) {
        util.showSavedIndicator(event);
        self.bubbleVisible(false);
        self.name("");
      })
      .call();
  };

};