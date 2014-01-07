LUPAPISTE.ConstructionStateChangeModel = function() {
  var self = this;
  self.dialogSelector = "#dialog-construction-state-change";
  self.datepickerSelector = "#construction-state-change-date";

  self.configs = {
      "start" : {commandName : "inform-construction-started",
                 getCommandData : function() {
                                    return {id:                  self.appId,
                                            startedTimestampStr: $(self.datepickerSelector).val()};
                                  },
                 dateSelectorLabel   : "constructionStarted.startedDate",
                 dialogHeader        : "constructionStarted.dialog.header",
                 dialogHelpParagraph : "constructionStarted.dialog.helpParagraph",
                 dialogButtonSend    : "constructionStarted.dialog.continue",
                 areYouSureMessage   : "constructionStarted.dialog.areyousure.message"},

      "ready" : {commandName : "inform-construction-ready",
                 getCommandData : function() {
                                    return {id:                self.appId,
                                            readyTimestampStr: $(self.datepickerSelector).val(),
                                            lang:              loc.getCurrentLanguage()};
                                  },
                 dateSelectorLabel   : "constructionReady.readyDate",
                 dialogHeader        : "constructionReady.dialog.header",
                 dialogHelpParagraph : "constructionReady.dialog.helpParagraph",
                 dialogButtonSend    : "constructionReady.dialog.continue",
                 areYouSureMessage   : "constructionReady.dialog.areyousure.message"}
  };

  self.appId = 0;
  self.constructionStateChangeDate = ko.observable(null);
  self.errorMessage = ko.observable(null);
  self.processing = ko.observable();
  self.pending = ko.observable();

  self.config = null;
  self.dialogHeader = ko.observable("");
  self.dateSelectorLabel = ko.observable("");
  self.dialogHelpParagraph = ko.observable("");
  self.dialogButtonSend = ko.observable("");
  self.areYouSureMessage = ko.observable("");


  self.ok = ko.computed( function() {return self.constructionStateChangeDate();} );

  self.onError = function(resp) {
    self.errorMessage(resp.text);
  };

  self.reset = function(app) {
    self.appId = app.id();
    self.constructionStateChangeDate(null);
    self.errorMessage(null);
    self.processing(false);
    self.pending(false);

    self.dialogHeader(self.config.dialogHeader);
    self.dateSelectorLabel(self.config.dateSelectorLabel);
    self.dialogHelpParagraph(self.config.dialogHelpParagraph);
    self.dialogButtonSend(self.config.dialogButtonSend);
    self.areYouSureMessage(self.config.areYouSureMessage);
  };

  self.doSend = function() {
    ajax.command(self.config.commandName, self.config.getCommandData())
      .processing(self.processing)
      .pending(self.pending)
      .success(function() {
        repository.load(self.appId);
        $("#construction-state-change").show();
        LUPAPISTE.ModalDialog.close();
        self.errorMessage(null);
        self.constructionStateChangeDate(null);
      })
      .error(self.onError)
      .call();
    return false;
  };

  self.doCancel = function() {
    $("#construction-state-change").show();
  }

  self.sendConstructionStateChangeDateInfo = function() {
    $("#construction-state-change").hide();
    LUPAPISTE.ModalDialog.showDynamicYesNo(
        loc("areyousure"),
        loc(self.config.areYouSureMessage, $(self.datepickerSelector).val()),
        {title: loc("yes"), fn: self.doSend},
        {title: loc("no"), fn: self.doCancel}
      );
  };

  //Open the dialog

  function openConstructionStateChangeDialog(stateChangeType, app) {
    self.config = null;
    self.config = self.configs[stateChangeType];
    if (self.config) {
      self.reset(app);
      LUPAPISTE.ModalDialog.open(self.dialogSelector);
    }
  };

  self.openConstructionStartDialog = _.partial(openConstructionStateChangeDialog, "start");
  self.openConstructionReadyDialog = _.partial(openConstructionStateChangeDialog, "ready");

};
