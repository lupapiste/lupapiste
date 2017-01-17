LUPAPISTE.InspectionSummaryTemplatesListModel = function() {
  "use strict"

  var self = this;

  ko.utils.extend(self, new LUPAPISTE.ComponentBaseModel());

  lupapisteApp.services.inspectionSummaryService.getTemplatesAsAuthorityAdmin();

  self.templates = ko.observableArray([]);

  self.newTemplateBubbleVisible = ko.observable(false);

  self.toggleNewTemplateBubble = function() {
    var old = self.newTemplateBubbleVisible();
    self.newTemplateBubbleVisible(!old);
  };

  self.addEventListener("inspectionSummaryService", "templatesLoaded", function(event) {
    self.templates(event.templates);
  });

  self.openDetails = function(data) {
    console.log(data);
  };
};