LUPAPISTE.StatementsTableModel = function(params) {
  "use strict";
  var self = this;

//  console.log("params: ", params);

  self.applicationId = params.applicationId;
  self.statements = params.statements;
  self.authorization = params.authModel;
  self.localisationKeys = params.localisationKeys;


// TODO: This is a copy from statement.js, will remove this duplication when moving this to a separate knockout component.
  var deleteStatementFromServer = function(statementId) {
    ajax
      .command("delete-statement", {id: self.applicationId(), statementId: statementId})
      .success(function() {
        repository.load(self.applicationId());
        return false;
      })
      .call();
    return false;
  };

  self.openStatement = function(model) {
    pageutil.openPage("statement", self.applicationId() + "/" + model.id());
    return false;
  };

  self.openDeleteDialog = function(model) {
    LUPAPISTE.ModalDialog.showDynamicYesNo(
        loc("statement.delete.header"),
        loc("statement.delete.message"),
        {title: loc("yes"), fn: _.partial(deleteStatementFromServer, model.id())},
        {title: loc("no")}
    );
  };

};