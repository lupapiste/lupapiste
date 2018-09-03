LUPAPISTE.EditDueDateModel = function( params ) {
  "use strict";
  var self = this;

  var date = params.data.dueDate;

  self.newDueDate = self.disposedPureComputed({
    read: function () {
      console.log("Read");
      return util.getIn(params, ["data", "dueDate"]);
    },
    write: function (value) {
      console.log("Write");
      params.data().dueDate(value);
    }
  });

};