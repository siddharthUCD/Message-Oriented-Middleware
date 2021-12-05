package service.message;

import service.core.ClientInfo;
import service.core.Quotation;

import java.io.Serializable;
import java.util.LinkedList;
import java.util.List;

public class ClientApplicationMessage implements Serializable {
    public Long id;
    public ClientInfo info = new ClientInfo();
    public List<Quotation> quotationsList = new LinkedList<Quotation>();

    public ClientApplicationMessage(Long Id, ClientInfo Info, List<Quotation> Quotations){
        this.id = Id;
        this.info = Info;
        this.quotationsList = Quotations;
    }

    public void addQuotation(Quotation quotation){
        if(this.quotationsList == null){
            this.quotationsList = new LinkedList<>();
        }

        if(quotation != null){
            this.quotationsList.add(quotation);
        }
    }
}
