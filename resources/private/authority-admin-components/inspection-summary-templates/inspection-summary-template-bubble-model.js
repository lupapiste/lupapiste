LUPAPISTE.InspectionSummaryTemplateBubbleModel = function(params) {
  "use strict";
  var self = this;

  ko.utils.extend(self, new LUPAPISTE.ComponentBaseModel());

  self.templateId = ko.observable();
  self.name = ko.observable("bar");
  self.itemsText = ko.observable("bar1");
  self.bubbleVisible = params.bubbleVisible;

  self.okVisible = ko.observable(true);
  self.cancelVisible = ko.observable(true);


};