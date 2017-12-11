LUPAPISTE.PremisesDownloadModel = function (params) {
    "use strict";
    var self = this;

    ko.utils.extend(self, LUPAPISTE.ComponentBaseModel(params));
    self.componentTemplate = "icon-button-template";

    self.waiting = ko.observable();
    self.buttonClass = params.buttonClass;
    self.buttonType = params.buttonType;
    self.buttonText = params.buttonText;
    self.iconClass =  params.iconClass;
    self.click = params.clickFn;
    self.testId = params.testId;
    self.right = false;
    self.isDisabled = self.waiting;

    self.applicationId = params.applicationId;
    self.documentId = params.documentId;

  // self.downloadPremises = function () {
  //     ajax.get("/api/raw/download-premises")
  //          .pending( waiting )
  //          .call();
  //  };

    self.downloadPremises = function () {
        alert("kiikkikii");
    };

};