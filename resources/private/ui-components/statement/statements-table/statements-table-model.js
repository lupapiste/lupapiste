LUPAPISTE.StatementsTableModel = function(params) {
  "use strict";
  var self = this;

  self.application = params.application;
  self.statements = params.statements;
  self.authorization = params.authModel;
  self.localisationKeys = params.localisationKeys;


  self.statementIdsWithAttachments = ko.pureComputed(function() {
    var statementIdsWithAttachments = [];
    _.forEach(lupapisteApp.models.application.attachments(), function(attachment) {
      var target = ko.mapping.toJS(attachment.target);
      if (target && target.type == "statement") {
        statementIdsWithAttachments.push(target.id);
      }
    });
    return _.unique(statementIdsWithAttachments);
  });

  self.hasAttachment = function(statement) {
    return _.includes(self.statementIdsWithAttachments(), statement.id());
  };

  self.isGiven = function(statement) {
    return _.contains(["given", "replyable", "replied", "closed"], util.getIn(statement, ["state"]));
  }

  var isAuthorityOrStatementOwner = function(statement) {
    var currentUser = lupapisteApp.models.currentUser;
    return currentUser.isAuthority() || util.getIn(statement, ["person", "userId"]) === currentUser.id();
  };

  self.isRemovable = function(statement) {
    return isAuthorityOrStatementOwner(statement) && !self.isGiven(statement);
  }

  self.canAccessStatement = function(statement) {
    return self.isGiven(statement) || isAuthorityOrStatementOwner(statement);
  };

  self.isStatementOverDue = function(statement) {
    var nowTimeInMs = new Date().getTime();
    return (statement.dueDate && self.isGiven(statement) ) ? (nowTimeInMs > statement.dueDate()) : false;
  };

  var deleteStatementFromServer = function(statementId) {
    ajax
      .command("delete-statement", {id: self.application.id(), statementId: statementId})
      .success(function() {
        repository.load(self.application.id());
        return false;
      })
      .call();
    return false;
  };

  self.openStatement = function(model) {
    pageutil.openPage("statement", self.application.id() + "/" + model.id());
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
