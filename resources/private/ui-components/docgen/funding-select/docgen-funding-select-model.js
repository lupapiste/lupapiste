LUPAPISTE.DocgenFundingSelectModel = function(params) {
  "use strict";
  var self = this;

  ko.utils.extend(self, new LUPAPISTE.DocgenInputModel(params));

  var service = params.service || lupapisteApp.services.documentDataService;

  self.selectValue = ko.observable( self.value() );
  self.indicator = ko.observable().extend({notify: "always"});

  var latestSaved = self.value();

  var doSave = function (value) {
    if( latestSaved !== value ) {
      service.updateDoc(self.documentId,
        [[params.path, value]],
        self.indicator);
      latestSaved = value;
    }
  };

  var saveFunding = function(value) {
    doSave(value);
    //invite
  };

  var reset = function() {
    self.selectValue(false);
  };

  self.disposedSubscribe(self.selectValue, function (value) {
    if (value === true) {
      LUPAPISTE.ModalDialog.showDynamicYesNo(
        loc("hankkeen-kuvaus.rahoitus.areysure"),
        loc("hankkeen-kuvaus.rahoitus.help"),
        {title: loc("yes"), fn: function() {saveFunding(value)}},
        {title: loc("no"), fn: function() {reset()}}
      );
    } else {
      doSave(value);
    }
  });
};
