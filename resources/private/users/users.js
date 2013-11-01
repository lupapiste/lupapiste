var users = (function() {
  "use strict";

  function toLoc(data, type, row) { return loc(data); }
  function toActive(data, type, row) { return loc(["users.data.enabled", data]); }
  
  function toOrgs(data, type, row) { return data ? data.join(", ") : ""; }
  
  function addOps(td, sData, oData, iRow, iCol) {
    var td = $(td).empty().text("");
    _.each(oData[5], function(op) {
      td.append($("<a>")
        .attr("href", "#")
        .data("op", op)
        .text("[" + loc("users.op." + op) + "]"));
    });
  }
  
  var dataTableConfig = {
    bProcessing:      true, // don't hide this, it brakes layout.
    bServerSide:      true,
    sAjaxSource:      "",
    aoColumnDefs:     [{aTargets: [2], mRender: toLoc},
                       {aTargets: [3], mRender: toOrgs, bSortable: false},
                       {aTargets: [4], mRender: toActive},
                       {aTargets: [5], fnCreatedCell: addOps, bSortable: false}],
    aaSorting:        [[0, "desc"]],
    sDom:             "<t><<r><p><i><l>>", // <'table-filter' f>
    iDisplayLength:   10,
    oLanguage:        {sLengthMenu:   loc("applications.lengthMenu"),
                       sProcessing:   "&nbsp;",
                       sSearch:       loc("search") + ":",
                       sZeroRecords:  loc("applications.welcome"),
                       oPaginate:     {"sNext": loc("next"), "sPrevious": loc("previous")},
                       sInfo:         loc("applications.results"),
                       sInfoFiltered: ""}
  };

  function UsersModel(table) {
    var self = this;
    self.table = table;
    
    self.filter = {
      role: ko.observable("foozzaa")
    };
    
    self.ops = function(row) {
      return ["disable", "edit", "resetPassword"];
    };
    
    self.processResults = function(r) {
      console.log("r:", r);
      var data = r.data;
      _.each(data.rows, function(row) { return row.push(self.ops(row)); });
      return {aaData:               data.rows,
              iTotalRecords:        data.total,
              iTotalDisplayRecords: data.display,
              sEcho:                data.echo};
    };
    
    self.fetch = function(source, data, callback) {
      var params = _(data)
        .concat(_.map(self.filter, function(v, k) { return {name: "filter-" + k, value: v()}; }))
        .reduce(function(m, p) { m[p.name] = p.value; return m; }, {});
      ajax
        .command("users-for-datatables")
        .json({params: params})
        .success(_.compose(callback, self.processResults))
        .call();
    };

    self.rowCreated = function(row, data) {
      $(row).attr("data-id", "data.id");
    };

    var config = _.clone(dataTableConfig);
    config.fnServerData = self.fetch;
    config.fnCreatedRow = self.rowCreated;
    self.table.dataTable(config);
    
    self.table.click(function(e) { console.log("click:", $(e)); });
  }
  
  var template;
  
  $(function() {
    template = $("#users-table table").clone();
    $("th", template).each(function(i, e) {
      var th = $(e);
      th.text(loc(th.text()));
    });
  });
  
  return {
    create: function(elementOrId) {
      var table = template.clone();
      var div = _.isString(elementOrId) ? $("#" + elementOrId) : elementOrId;
      div.append(table);
      return new UsersModel(table);
    }
  };

})();
