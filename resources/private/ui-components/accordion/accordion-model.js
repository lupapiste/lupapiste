LUPAPISTE.AccordionModel = function(params) {
  "use strict";
  var self = this;
  ko.utils.extend(self, new LUPAPISTE.ComponentBaseModel(params));

  self.ltitle = params.ltitle;
  self.contentIsTemplate = params.accordionContentTemplate !== undefined;
  self.contentIsComponent = params.accordionContentComponent !== undefined;
  self.contents = self.contentIsTemplate ? {
    name: params.accordionContentTemplate,
    data: params.accordionContentTemplateData
  } : {
    name:   params.accordionContentComponent,
    params: params.accordionContentComponentParams
  };
};
