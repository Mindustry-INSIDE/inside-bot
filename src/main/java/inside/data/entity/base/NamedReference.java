package inside.data.entity.base;

import discord4j.common.util.Snowflake;
import reactor.util.annotation.Nullable;

import javax.persistence.*;
import java.util.Objects;

@Embeddable
public class NamedReference{
    @Column
    private String id;

    @Column
    private String name;

    @Column
    private String discriminator;

    protected NamedReference(){}

    public NamedReference(Snowflake id, String name, String discriminator){
        this.id = Objects.requireNonNull(id, "id").asString();
        this.name = Objects.requireNonNull(name, "name");
        this.discriminator = Objects.requireNonNull(discriminator, "discriminator");
    }

    public NamedReference(Snowflake id, String name){
        this.id = Objects.requireNonNull(id, "id").asString();
        this.name = Objects.requireNonNull(name, "name");
    }

    public String id(){
        return id;
    }

    public void id(String id){
        this.id = Objects.requireNonNull(id, "id");
    }

    public String name(){
        return name;
    }

    public void name(String name){
        this.name = Objects.requireNonNull(name, "name");
    }

    @Nullable
    public String discriminator(){
        return discriminator;
    }

    public void discriminator(String discriminator){
        this.discriminator = Objects.requireNonNull(discriminator, "discriminator");
    }

    @Override
    public boolean equals(Object o){
        if(this == o) return true;
        if(o == null || getClass() != o.getClass()) return false;
        NamedReference that = (NamedReference)o;
        return id.equals(that.id) && name.equals(that.name) &&
                Objects.equals(discriminator, that.discriminator);
    }

    @Override
    public int hashCode(){
        return Objects.hash(id, name, discriminator);
    }

    @Override
    public String toString(){
        return "NamedReference{" +
                "id='" + id + '\'' +
                ", name='" + name + '\'' +
                ", discriminator='" + discriminator + '\'' +
                '}';
    }
}
