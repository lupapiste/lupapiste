LUPAPISTE.SelectInspectionSummaryTemplateForOperationModel = function(params) {
  "use strict";

  var self = this;

  ko.utils.extend(self, new LUPAPISTE.ComponentBaseModel());

  self.templates = params.templates;
  self.operationId = _.get(params.operation, "id");
  self.selectedTemplate = ko.observable();
  self.selectedTemplateFromBackend = null;

  self.disposedComputed(function() {
    var selectionId = _.get(params.operationsMapping(), self.operationId);
    self.selectedTemplateFromBackend = _.find(params.templates(), ["id", selectionId]);
    self.selectedTemplate(self.selectedTemplateFromBackend);
  });

  function save(d) {
    if (_.get(d, "id") !== _.get(self.selectedTemplateFromBackend, "id")) {
      lupapisteApp.services.inspectionSummaryService.selectTemplateForOperation(self.operationId, _.get(d, "id"));
    }
  }

  self.disposedSubscribe(self.selectedTemplate, _.debounce(save, 500));

};